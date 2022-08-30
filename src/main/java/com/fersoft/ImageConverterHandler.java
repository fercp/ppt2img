package com.fersoft;


import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2ProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2ProxyResponseEvent;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.poi.sl.draw.Drawable;
import org.apache.poi.sl.usermodel.Slide;
import org.apache.poi.sl.usermodel.SlideShow;
import org.apache.poi.sl.usermodel.SlideShowFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.HashMap;

public class ImageConverterHandler implements RequestHandler<APIGatewayV2ProxyRequestEvent, APIGatewayV2ProxyResponseEvent> {
    Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public APIGatewayV2ProxyResponseEvent handleRequest(APIGatewayV2ProxyRequestEvent event, Context context) {
        LambdaLogger logger = context.getLogger();
        APIGatewayV2ProxyResponseEvent response = new APIGatewayV2ProxyResponseEvent();
        response.setIsBase64Encoded(false);
        response.setStatusCode(200);
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "text/html");
        response.setHeaders(headers);

        // log execution details
        logger.log("Body:" + event.getBody());
        try {
            response.setBody("<!DOCTYPE html><html><head><title>AWS PPT to PNG</title></head><body>" +
                    "<h1>You can reach png file from </h1><p>" + convertToPng(event.getBody().trim()) + "</p>" +
                    "</body></html>");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Util.logEnvironment(event, context, gson);
        return response;
    }

    private String convertToPng(String url) throws IOException {
        BufferedInputStream in = new BufferedInputStream(new URL(url).openStream());
        double scale = 1.5;
        try (SlideShow<?, ?> ss = SlideShowFactory.create(in, null)) {
            Dimension pgsize = ss.getPageSize();
            int width = (int) (pgsize.width * scale);
            int height = (int) (pgsize.height * scale);
            Slide<?, ?> slide = ss.getSlides().get(0);
            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = img.createGraphics();
            // default rendering options
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            graphics.setRenderingHint(Drawable.BUFFERED_IMAGE, new WeakReference<>(img));
            graphics.scale(scale, scale);
            // draw stuff
            slide.draw(graphics);

            String[] parts = url.split("/");
            File output = new File("/tmp/" + parts[parts.length - 1] + ".png");
            ImageIO.write(img, "PNG", output);
            graphics.dispose();
            img.flush();
            final AmazonS3 s3 = AmazonS3ClientBuilder.standard().withRegion(Regions.EU_CENTRAL_1).build();
            s3.putObject("fercp-test-2", output.getName(), output);
            return s3.getUrl("fercp-test-2", output.getName()).toExternalForm();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

