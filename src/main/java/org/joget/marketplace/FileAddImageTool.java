package org.joget.marketplace;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import org.joget.apps.app.model.AppDefinition;

import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FileUtil;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.workflow.model.WorkflowAssignment;
import org.springframework.context.ApplicationContext;

import com.lowagie.text.Image;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfStamper;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public class FileAddImageTool extends DefaultApplicationPlugin {

    private final static String MESSAGE_PATH = "messages/FileAddImageTool";

    @Override
    public String getName() {
        return AppPluginUtil.getMessage("org.joget.marketplace.FileAddImageTool.pluginLabel", getClassName(), MESSAGE_PATH);
    }

    @Override
    public String getVersion() {
        return "7.0.4";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getLabel() {
        //support i18n
        return AppPluginUtil.getMessage("org.joget.marketplace.FileAddImageTool.pluginLabel", getClassName(), MESSAGE_PATH);
    }

    @Override
    public String getDescription() {
        //support i18n
        return AppPluginUtil.getMessage("org.joget.marketplace.FileAddImageTool.pluginDesc", getClassName(), MESSAGE_PATH);
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/FileAddImageTool.json", null, true, MESSAGE_PATH);
    }

    @Override
    public Object execute(Map map) {
        ApplicationContext ac = AppUtil.getApplicationContext();
        AppService appService = (AppService) ac.getBean("appService");
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();

        // source file
        String formDefIdSourceFile = (String) map.get("formDefIdSourceFile");
        String sourceFileFieldId = (String) map.get("sourceFileFieldId");
        String sourceFileRecordId = (String) map.get("sourceFileRecordId");

        // output file
        String formDefIdOutputFile = (String) map.get("formDefIdOutputFile");
        String outputFileFieldId = (String) map.get("outputFileFieldId");
        String outputFileRecordId = (String) map.get("outputFileRecordId");

        // data to add into pdf
        String addImage = (String) map.get("addImage");
        String addText = (String) map.get("addText");
        String sourceImgFormDefId = (String) map.get("sourceImgFormDefId");
        String sourceImgFieldId = (String) map.get("sourceImgFieldId");
        String sourceImgRecordId = (String) map.get("sourceImgRecordId");
        String textFontSize = (String) map.get("textFontSize");
        String textColor = (String) map.get("textColor");
        String text = (String) map.get("text");

        // positions
        String sourceImgPosition = (String) map.get("sourceImgPosition");
        String textPosition = (String) map.get("textPosition");
        // static position
        String positionDirectionImg = (String) map.get("positionDirectionImg");
        String positionDirectionText = (String) map.get("positionDirectionText");
        // manual position
        String sourceImgPositionx = (String) map.get("sourceImgPositionx");
        String sourceImgPositiony = (String) map.get("sourceImgPositiony");
        String textPositionx = (String) map.get("textPositionx");
        String textPositiony = (String) map.get("textPositiony");

        String sourceImgPadding = (String) map.get("sourceImgPadding");
        String textPadding = (String) map.get("textPadding");

        WorkflowAssignment wfAssignment = (WorkflowAssignment) map.get("workflowAssignment");
        if (wfAssignment != null) {
            if (sourceFileRecordId != null && sourceFileRecordId.equals("")) {
                sourceFileRecordId = appService.getOriginProcessId(wfAssignment.getProcessId());
            }
            if (sourceImgRecordId != null && sourceImgRecordId.equals("")) {
                sourceImgRecordId = appService.getOriginProcessId(wfAssignment.getProcessId());
            }
            if (outputFileRecordId != null && outputFileRecordId.equals("")) {
                outputFileRecordId = appService.getOriginProcessId(wfAssignment.getProcessId());
            }
        }

        Form loadForm = null;
        File srcFile = null;
        File imageToAdd = null;

        if (formDefIdSourceFile != null && formDefIdOutputFile != null) {
            try {
                FormData formData = new FormData();

                // get pdf
                formData.setPrimaryKeyValue(sourceFileRecordId);
                loadForm = appService.viewDataForm(appDef.getId(), appDef.getVersion().toString(), formDefIdSourceFile, null, null, null, formData, null, null);
                Element el = FormUtil.findElement(sourceFileFieldId, loadForm, formData);
                srcFile = FileUtil.getFile(FormUtil.getElementPropertyValue(el, formData), loadForm, sourceFileRecordId);

                String filePaths = srcFile.getPath();
                FormRowSet frs = new FormRowSet();

                List<String> fileList = getFilesList(filePaths);
                StringBuilder resultBuilder = new StringBuilder();
              
                for (String filePath : fileList) {
                    File uploadedFile = new File(filePath.trim());
                    byte[] srcPDFFileContent;
                    byte[] srcImgFileContent = new byte[0];
                    String fileName;
                    try (FileInputStream fileIS = new FileInputStream(uploadedFile)) {
                        srcPDFFileContent = fileIS.readAllBytes();
                    }

                    String fileExt = FilenameUtils.getExtension(uploadedFile.getName());
                    ByteArrayOutputStream os = new ByteArrayOutputStream();
                    String dataType = "";

                    // get image to be added
                    if (sourceImgRecordId != null) {
                        formData.setPrimaryKeyValue(sourceImgRecordId);
                        loadForm = appService.viewDataForm(appDef.getId(), appDef.getVersion().toString(), sourceImgFormDefId, null, null, null, formData, null, null);
                        Element elImg = FormUtil.findElement(sourceImgFieldId, loadForm, formData);
                        imageToAdd = FileUtil.getFile(FormUtil.getElementPropertyValue(elImg, formData), loadForm, sourceImgRecordId);
                        FileInputStream fileISImg = new FileInputStream(imageToAdd);
                        srcImgFileContent = fileISImg.readAllBytes();
                        fileISImg.close();
                    }
                   
                    if (fileExt.equalsIgnoreCase("pdf")) {
                        // image
                        if (addImage != null && addImage.equals("true")) {
                            BufferedImage stampImage = ImageIO.read(new File(imageToAdd.getPath()));
                            byte[] outputPDFFileContent = addImageToPDF(srcPDFFileContent, srcImgFileContent, stampImage, fileExt, sourceImgPosition, positionDirectionImg, sourceImgPositionx, sourceImgPositiony, sourceImgPadding);
                            os.write(outputPDFFileContent);
                            dataType = "image";
                        }
                        
                        if (addText != null && addText.equals("true")) {
                            byte[] textOutputPDFFileContent;
                            // image + text
                            if (os.size() > 0) {
                                textOutputPDFFileContent = addTextToPDF(os.toByteArray(), text, textFontSize, textColor, fileExt, textPosition, positionDirectionText, textPositionx, textPositiony, textPadding);
                                dataType = "imageText";
                            // text
                            } else {
                                textOutputPDFFileContent = addTextToPDF(srcPDFFileContent, text, textFontSize, textColor, fileExt, textPosition, positionDirectionText, textPositionx, textPositiony, textPadding);
                                dataType = "text";
                            }
                            os.write(textOutputPDFFileContent);
                        } 
                        
                        // write to file
                        if (os.size() > 0) {
                            fileName = writeFile(uploadedFile, appService, appDef, formDefIdOutputFile, outputFileRecordId, os.toByteArray(), "pdf", dataType);
                        } else {
                            // if no image and text, return original file
                            fileName = writeFile(uploadedFile, appService, appDef, formDefIdOutputFile, outputFileRecordId, srcPDFFileContent, "pdf", dataType);
                        }
                        if (resultBuilder.length() > 0) {
                            resultBuilder.append(";");
                        }
                        resultBuilder.append(fileName);
                    } else if (fileExt.equalsIgnoreCase("png") || fileExt.equalsIgnoreCase("jpg") || fileExt.equalsIgnoreCase("jpeg")) {         
                        BufferedImage sourceImage = ImageIO.read(new File(uploadedFile.getPath()));
                        if (addImage != null && addImage.equals("true") && sourceImgRecordId != null) {
                            BufferedImage stampImage = ImageIO.read(new File(imageToAdd.getPath()));
                            BufferedImage combinedImageInImage = addImageToImage(sourceImage, stampImage, fileExt, sourceImgPosition, positionDirectionImg, sourceImgPositionx, sourceImgPositiony, sourceImgPadding);
                            if (combinedImageInImage != null) {
                                if (addText != null && addText.equals("true")) {
                                    // image + text
                                    BufferedImage combinedImage = addTextToImage(combinedImageInImage, text, textFontSize, textColor, fileExt, textPosition, positionDirectionText, textPositionx, textPositiony, textPadding);
                                    ImageIO.write(combinedImage, FilenameUtils.getExtension(uploadedFile.getName()), os);
                                    dataType = "imageText";
                                } else {
                                    // image
                                    ImageIO.write(combinedImageInImage, FilenameUtils.getExtension(uploadedFile.getName()), os);
                                    dataType = "image";
                                }
                             }
                        } else if (addText != null && addText.equals("true")) {
                            // text
                            BufferedImage combinedImage = addTextToImage(sourceImage, text, textFontSize, textColor, fileExt, textPosition, positionDirectionText, textPositionx, textPositiony, textPadding);
                            ImageIO.write(combinedImage, FilenameUtils.getExtension(uploadedFile.getName()), os);
                            dataType = "text";
                        } 

                        // if no image and text, return original file
                        if (os.size() <= 0) {
                            ImageIO.write(sourceImage, FilenameUtils.getExtension(uploadedFile.getName()), os);
                        }

                        // write to file
                        fileName = writeFile(uploadedFile, appService, appDef, formDefIdOutputFile, outputFileRecordId, os.toByteArray(), FilenameUtils.getExtension(uploadedFile.getName()), dataType);
                    
                        if (resultBuilder.length() > 0) {
                            resultBuilder.append(";");
                        }
                        resultBuilder.append(fileName);
                    }
                }

                if (!fileList.isEmpty()) {
                    FormRow row = new FormRow();
                    row.put(outputFileFieldId, resultBuilder.toString());
                    frs.add(row);
                    appService.storeFormData(appDef.getAppId(), appDef.getVersion().toString(), formDefIdOutputFile, frs, outputFileRecordId);
                }

            } catch (Exception ex) {
                LogUtil.error(getClassName(), ex, ex.getMessage());
            }
        }
        return null;
    }

    private String writeFile(File uploadedFile, AppService appService, AppDefinition appDef, String formDefIdOutputFile, String outputFileRecordId, byte[] outputPDFFileContent, String extension, String dataType) throws IOException {
        // output in new pdf
        String fileNameWithOutExt = FilenameUtils.removeExtension(uploadedFile.getName());
        String fileName = fileNameWithOutExt + "." + extension;
        if (dataType.equals("image")){
            fileName = fileNameWithOutExt + "_withImage."+extension;
        } else if (dataType.equals("text")){
            fileName = fileNameWithOutExt + "_withText."+extension;
        } else if (dataType.equals("imageText")){
            fileName = fileNameWithOutExt + "_withImageAndText."+extension;
        } else {
            fileName = fileNameWithOutExt + "." + extension;
        }
    
        String tableName = appService.getFormTableName(appDef, formDefIdOutputFile);
        String path = FileUtil.getUploadPath(tableName, outputFileRecordId);
        final File file = new File(path + fileName);
        FileUtils.writeByteArrayToFile(file, outputPDFFileContent);
        return fileName;
    }

    public List<String> getFilesList(String filePaths) {
        String[] fileArray = filePaths.split(";");
        List<String> fileList = new ArrayList<>();

        String directoryPath = "";
        for (String filePath : fileArray) {
            String fullPath = "";
            String trimmedPath = filePath.trim();
            int lastSeparatorIndex = trimmedPath.lastIndexOf(File.separator);
            if (lastSeparatorIndex != -1) {
                directoryPath = trimmedPath.substring(0, lastSeparatorIndex);
                String fileName = trimmedPath.substring(lastSeparatorIndex + 1);
                fullPath = directoryPath + File.separator + fileName;
            } else {
                fullPath = directoryPath + File.separator + trimmedPath;
            }
            fileList.add(fullPath);
        }
        return fileList;
    }

    public BufferedImage addImageToImage(BufferedImage sourceImage, BufferedImage stampImage, String fileExt, String position, String positionDirection, String srcX, String srcY, String padding) {
        BufferedImage combinedImage = new BufferedImage(
                sourceImage.getWidth(),
                sourceImage.getHeight(),
                sourceImage.getType());
        Graphics2D g2d = combinedImage.createGraphics();

        // get x,y
        String finalPositionImg = adjustPosition(sourceImage.getWidth(), sourceImage.getHeight(), stampImage.getWidth(), stampImage.getHeight(), fileExt, position, positionDirection, srcX, srcY, padding);
        String[] partsImg = finalPositionImg.split(",");
        int x = Integer.parseInt(partsImg[0].trim());
        int y = Integer.parseInt(partsImg[1].trim());

        g2d.drawImage(sourceImage, 0, 0, null);
        // int stampX = 0;
        // int stampY = sourceImage.getHeight() - stampImage.getHeight();
        g2d.drawImage(stampImage, x, y, null);
        g2d.dispose();
        return combinedImage;
    }

    public BufferedImage addTextToImage(BufferedImage sourceImage, String text, String fontSizeStr, String textColor, String fileExt, String position, String positionDirection, String srcX, String srcY, String padding) {
        Graphics2D g2d = sourceImage.createGraphics();

        int fontSize = Integer.parseInt(fontSizeStr);
        Font font = new Font("Arial", Font.BOLD, fontSize);
        
        // get width and height of text
        String widthHeightText = getStringWidth(text, font);
        String[] partsWidthHeightText= widthHeightText.split(",");
        int widthText = Integer.parseInt(partsWidthHeightText[0].trim());
        int heightText = Integer.parseInt(partsWidthHeightText[1].trim());
          
        // get x,y
        String finalPositionImg = adjustPosition(sourceImage.getWidth(), sourceImage.getHeight(), widthText, heightText, fileExt, position, positionDirection, srcX, srcY, padding);
        String[] partsImg = finalPositionImg.split(",");
        int x = Integer.parseInt(partsImg[0].trim());
        int y = Integer.parseInt(partsImg[1].trim()) + 10;


        Color color = Color.decode(textColor);                     
        g2d.setColor(color);
        g2d.setFont(font);

        g2d.drawString(text, x, y);
        g2d.dispose();

        return sourceImage;
    }

    public byte[] addImageToPDF(byte[] srcPDF, byte[] srcImg, BufferedImage stampImage,  String fileExt, String position, String positionDirection, String srcX, String srcY, String padding) throws Exception {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
    
        PdfReader reader = new PdfReader(srcPDF);
        PdfStamper stamper = new PdfStamper(reader, os);
    
        int totalPages = reader.getNumberOfPages();

        // get x,y
        String finalPositionImg = adjustPosition((int)reader.getPageSize(1).getWidth(), (int)reader.getPageSize(1).getHeight(), stampImage.getWidth(), stampImage.getHeight(), fileExt, position, positionDirection, srcX, srcY, padding);
        String[] partsImg = finalPositionImg.split(",");
        int x = Integer.parseInt(partsImg[0].trim());
        int y = Integer.parseInt(partsImg[1].trim());
    
        for (int i = 1; i <= totalPages; i++) {
            PdfContentByte over = stamper.getOverContent(i);
    
            if (srcImg != null) {
                Image img = Image.getInstance(srcImg);
                img.setAbsolutePosition(x, y);
                over.addImage(img);
            } else {
                LogUtil.info(getClassName(), "Image missing");
            }
        }
        stamper.close();
        return os.toByteArray();
    }

    public byte[] addTextToPDF(byte[] srcPDF, String text, String fontSizeStr, String textColor, String fileExt, String position, String positionDirection, String srcX, String srcY, String padding) throws Exception {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
    
        PdfReader reader = new PdfReader(srcPDF);
        PdfStamper stamper = new PdfStamper(reader, os);
    
        int totalPages = reader.getNumberOfPages();
    
        int fontSize = Integer.parseInt(fontSizeStr);
        Font font = new Font("Arial", Font.BOLD, fontSize);
        Color color = Color.decode(textColor);  
        // get width and height of text
        String widthHeightText = getStringWidth(text, font);
        String[] partsWidthHeightText= widthHeightText.split(",");
        int widthText = Integer.parseInt(partsWidthHeightText[0].trim());
        int heightText = Integer.parseInt(partsWidthHeightText[1].trim());

        // get x,y
        String finalPositionImg = adjustPosition((int)reader.getPageSize(1).getWidth(), (int)reader.getPageSize(1).getHeight(), widthText, heightText, fileExt, position, positionDirection, srcX, srcY, padding);
        String[] partsImg = finalPositionImg.split(",");
        int x = Integer.parseInt(partsImg[0].trim());
        int y = Integer.parseInt(partsImg[1].trim());
    
        for (int i = 1; i <= totalPages; i++) {
            PdfContentByte over = stamper.getOverContent(i);
               
            over.beginText();
            over.setFontAndSize(BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.EMBEDDED), fontSize);
            over.setColorFill(color);
            over.setTextMatrix(x, y);
            over.showText(text);
            over.endText();
        }
        stamper.close();
        return os.toByteArray();
    }

    public String adjustPosition(int width, int height, int dataWidth, int dataHeight, String fileExt, String position, String positionDirection, String x, String y, String padding){
        int paddingInt = 0;
        if(padding != null && !padding.equals("")){
            paddingInt = Integer.parseInt(padding);
        }
        // adjust positions
        if (!position.equals("") && position != null) {
            // if static position
            if (position.equals("staticPosition") && positionDirection != null) {
              
                switch (positionDirection) {
                    case "topLeft":
                        if(fileExt.equals("pdf"))
                            return (0 + paddingInt) + "," + (height - dataHeight - paddingInt);
                        else 
                            return (0 + paddingInt) + "," + (0 + paddingInt);
                    case "topRight":
                        if(fileExt.equals("pdf"))
                            return (width - dataWidth - paddingInt) + "," + (height - dataHeight - paddingInt);
                        else 
                            return (width - dataWidth - paddingInt) + "," + (0 + paddingInt);
                    case "bottomLeft":
                        if(fileExt.equals("pdf"))
                            return (0 + paddingInt) + "," + (0 + paddingInt);
                        else 
                            return (0 + paddingInt) + "," + (height - dataHeight - paddingInt);
                    case "bottomRight":
                        if(fileExt.equals("pdf"))
                            return (width - dataWidth - paddingInt) + "," + (0 + paddingInt);
                        else 
                            return (width - dataWidth - paddingInt) + "," + (height - dataHeight - paddingInt);
                    default:
                        return (0 + paddingInt) + "," + (0 + paddingInt);
                }
            
            } else {
                // if manual position
                return x + "," + y;
            }
        }
        return (0 + paddingInt) + "," + (0 + paddingInt);
    }

    public static String getStringWidth(String text, Font font) {
        // Create a temporary image to get the Graphics2D context
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        // Set the font to the Graphics2D context
        g2d.setFont(font);

        // Get the FontMetrics for the specified font
        FontMetrics fontMetrics = g2d.getFontMetrics();

        // Get the width of the string
        int width = fontMetrics.stringWidth(text);
        int height = fontMetrics.getHeight();

        // Dispose of the Graphics2D context to free resources
        g2d.dispose();

        return width + "," + height;
    }
}
