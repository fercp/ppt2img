package com.fersoft;


import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.event.S3EventNotification.S3EventNotificationRecord;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.poi.sl.draw.Drawable;
import org.apache.poi.sl.usermodel.Slide;
import org.apache.poi.sl.usermodel.SlideShow;
import org.apache.poi.sl.usermodel.SlideShowFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class S3Handler implements RequestHandler<S3Event, String> {
    private static final Logger logger = LoggerFactory.getLogger(S3Handler.class);
    private final String PPT_TYPE = "ppt";
    private final String PPTX_TYPE = "pptx";

    Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public String handleRequest(S3Event s3event, Context context) {
        try {
            logger.info("EVENT: " + gson.toJson(s3event));
            S3EventNotificationRecord record = s3event.getRecords().get(0);

            String srcBucket = record.getS3().getBucket().getName();

            // Object key may have spaces or unicode non-ASCII characters.
            String srcKey = record.getS3().getObject().getUrlDecodedKey();


            // Infer the image type.
            Matcher matcher = Pattern.compile(".*\\.([^\\.]*)").matcher(srcKey);
            if (!matcher.matches()) {
                logger.info("Unable to infer file type for key " + srcKey);
                return "";
            }
            String fileType = matcher.group(1);
            if (!(PPT_TYPE.equalsIgnoreCase(fileType)) && !(PPTX_TYPE.equalsIgnoreCase(fileType))) {
                logger.info("Skipping non-ppt " + srcKey);
                return "";
            }

            // Download the image from S3 into a stream
            AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();
            S3Object s3Object = s3Client.getObject(new GetObjectRequest(
                    srcBucket, srcKey));
            InputStream objectData = s3Object.getObjectContent();

            return convertToPng(objectData, srcBucket, srcKey);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String convertToPng(InputStream in, String bucket, String fileName) throws IOException {
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
            String[] parts = fileName.split("/");
            File output = new File("/tmp/" + parts[parts.length - 1] + ".png");
            ImageIO.write(img, "PNG", output);
            graphics.dispose();
            img.flush();
            final AmazonS3 s3 = AmazonS3ClientBuilder.standard().withRegion(Regions.EU_CENTRAL_1).build();
            s3.putObject(bucket, output.getName(), output);
            return s3.getUrl(bucket, output.getName()).toExternalForm();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

