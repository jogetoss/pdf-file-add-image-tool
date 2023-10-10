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
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfStamper;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public class PDFFileAddImageTool extends DefaultApplicationPlugin {

    private final static String MESSAGE_PATH = "messages/PDFFileAddImageTool";

    @Override
    public String getName() {
        return AppPluginUtil.getMessage("org.joget.marketplace.PDFFileAddImageTool.pluginLabel", getClassName(), MESSAGE_PATH);
    }

    @Override
    public String getVersion() {
        return "7.0.1";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getLabel() {
        //support i18n
        return AppPluginUtil.getMessage("org.joget.marketplace.PDFFileAddImageTool.pluginLabel", getClassName(), MESSAGE_PATH);
    }

    @Override
    public String getDescription() {
        //support i18n
        return AppPluginUtil.getMessage("org.joget.marketplace.PDFFileAddImageTool.pluginDesc", getClassName(), MESSAGE_PATH);
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/PDFFileAddImageTool.json", null, true, MESSAGE_PATH);
    }

    @Override
    public Object execute(Map map) {
        ApplicationContext ac = AppUtil.getApplicationContext();
        AppService appService = (AppService) ac.getBean("appService");
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();

        String formDefIdSourceFile = (String) map.get("formDefIdSourceFile");
        String sourceImgFormDefId = (String) map.get("sourceImgFormDefId");
        String formDefIdOutputFile = (String) map.get("formDefIdOutputFile");
        String sourceFileFieldId = (String) map.get("sourceFileFieldId");
        String sourceImgFieldId = (String) map.get("sourceImgFieldId");
        String outputFileFieldId = (String) map.get("outputFileFieldId");
        String sourceFileRecordId = (String) map.get("sourceFileRecordId");
        String sourceImgRecordId = (String) map.get("sourceImgRecordId");
        String outputFileRecordId = (String) map.get("outputFileRecordId");

        WorkflowAssignment wfAssignment = (WorkflowAssignment) map.get("workflowAssignment");
        if (wfAssignment != null) {
            if (sourceFileRecordId.equals("")) {
                sourceFileRecordId = appService.getOriginProcessId(wfAssignment.getProcessId());
            }
            if (sourceImgRecordId.equals("")) {
                sourceImgRecordId = appService.getOriginProcessId(wfAssignment.getProcessId());
            }
            if (outputFileRecordId.equals("")) {
                outputFileRecordId = appService.getOriginProcessId(wfAssignment.getProcessId());
            }
        }

        Form loadForm = null;
        String primaryKey = null;
        File srcFile = null;
        File srcImg = null;

        if (formDefIdSourceFile != null && sourceImgFormDefId != null && formDefIdOutputFile != null) {
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
                    try (FileInputStream fileIS = new FileInputStream(uploadedFile)) {
                        srcPDFFileContent = fileIS.readAllBytes();
                    }

                    // get image
                    formData.setPrimaryKeyValue(sourceImgRecordId);
                    loadForm = appService.viewDataForm(appDef.getId(), appDef.getVersion().toString(), sourceImgFormDefId, null, null, null, formData, null, null);
                    Element elImg = FormUtil.findElement(sourceImgFieldId, loadForm, formData);
                    srcImg = FileUtil.getFile(FormUtil.getElementPropertyValue(elImg, formData), loadForm, sourceImgRecordId);
                    FileInputStream fileISImg = new FileInputStream(srcImg);
                    byte[] srcImgFileContent = fileISImg.readAllBytes();
                    fileISImg.close();

                    String fileExt = FilenameUtils.getExtension(uploadedFile.getName());
                    if (fileExt.equalsIgnoreCase("pdf")) {
                        byte[] outputPDFFileContent = addImagetoPDF(srcPDFFileContent, srcImgFileContent);
                        String fileName = writeFile(uploadedFile, appService, appDef, formDefIdOutputFile, outputFileRecordId, outputPDFFileContent, "pdf");
                        if (resultBuilder.length() > 0) {
                            resultBuilder.append(";");
                        }
                        resultBuilder.append(fileName);
                    } else if (fileExt.equalsIgnoreCase("png") || fileExt.equalsIgnoreCase("jpg") || fileExt.equalsIgnoreCase("jpeg")) {
                        BufferedImage combinedImage = addImageToImage(uploadedFile.getPath(), srcImg.getPath());
                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        ImageIO.write(combinedImage, FilenameUtils.getExtension(uploadedFile.getName()), outputStream);
                        byte[] byteArray = outputStream.toByteArray();
                        String fileName = writeFile(uploadedFile, appService, appDef, formDefIdOutputFile, outputFileRecordId, byteArray, FilenameUtils.getExtension(uploadedFile.getName()));
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

    private String writeFile(File uploadedFile, AppService appService, AppDefinition appDef, String formDefIdOutputFile, String outputFileRecordId, byte[] outputPDFFileContent, String extension) throws IOException {
        // output in new pdf
        String fileNameWithOutExt = FilenameUtils.removeExtension(uploadedFile.getName());
        String fileName = fileNameWithOutExt + "_withImage."+extension;
        String tableName = appService.getFormTableName(appDef, formDefIdOutputFile);
        String path = FileUtil.getUploadPath(tableName, outputFileRecordId);
        final File file = new File(path + fileName);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        FileUtils.writeByteArrayToFile(file, outputPDFFileContent);
        return fileName;
    }

    public String writeFile() {
        return null;
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

    public BufferedImage addImageToImage(String sourceImagePath, String stampImagePath) {
        try {
            BufferedImage sourceImage = ImageIO.read(new File(sourceImagePath));
            BufferedImage stampImage = ImageIO.read(new File(stampImagePath));
            BufferedImage combinedImage = new BufferedImage(
                    sourceImage.getWidth(),
                    sourceImage.getHeight(),
                    sourceImage.getType());
            Graphics2D g2d = combinedImage.createGraphics();
            g2d.drawImage(sourceImage, 0, 0, null);
            int stampX = 0;
            int stampY = sourceImage.getHeight() - stampImage.getHeight();
            g2d.drawImage(stampImage, stampX, stampY, null);
            g2d.dispose();
            return combinedImage;
        } catch (IOException ex) {
            LogUtil.error(getClassName(), ex, ex.getMessage());
            return null;
        }

    }

    public byte[] addImagetoPDF(byte[] srcPDF, byte[] srcImg) throws Exception {
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        try {
            PdfReader reader = new PdfReader(srcPDF);
            PdfStamper stamper = new PdfStamper(reader, os);
            PdfContentByte over = null;
            int totalPages = reader.getNumberOfPages();

            for (int i = 1; i <= totalPages; i++) {
                float left = 0;
                float top = 0;
                float bottom = reader.getPageSize(i).getHeight() - top;
                float right = reader.getPageSize(i).getRight();

                Image img = null;
                if (srcImg != null) {
                    img = Image.getInstance(srcImg);
                }

                if (img != null) {
                    // bottom left
                    img.setAbsolutePosition(left, top);

                    over = stamper.getOverContent(i);
                    over.addImage(img);
                } else {
                    LogUtil.info(getClass().getName(), "Image missing");
                }
            }
            stamper.close();

        } catch (Exception ex) {
            LogUtil.error(getClassName(), ex, ex.getMessage());
        }
        return os.toByteArray();
    }
}
