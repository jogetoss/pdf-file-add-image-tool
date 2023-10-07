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
import java.util.ArrayList;
import java.util.List;

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

        String formDefIdSourcePdf = (String) map.get("formDefId_SourcePDF");
        String formDefIdSourceImg = (String) map.get("formDefId_SourceIMG");
        String formDefIdOutputPdf = (String) map.get("formDefId_OutputPDF");
        String fieldIdSourcePdf = (String) map.get("fieldId_SourcePDF");
        String fieldIdSourceImg = (String) map.get("fieldId_SourceIMG");
        String fieldIdOutputPdf = (String) map.get("fieldId_OutputPDF");
        String recordIdSourcePdf = (String) map.get("recordId_SourcePDF");
        String recordIdSourceImg = (String) map.get("recordId_SourceIMG");
        String recordIdOutputPdf = (String) map.get("recordId_OutputPDF");

        WorkflowAssignment wfAssignment = (WorkflowAssignment) map.get("workflowAssignment");
        if (wfAssignment != null) {
            if (recordIdSourcePdf.equals("")) {
                recordIdSourcePdf = appService.getOriginProcessId(wfAssignment.getProcessId());
            }
            if (recordIdSourceImg.equals("")) {
                recordIdSourceImg = appService.getOriginProcessId(wfAssignment.getProcessId());
            }
            if (recordIdOutputPdf.equals("")) {
                recordIdOutputPdf = appService.getOriginProcessId(wfAssignment.getProcessId());
            }
        }

        Form loadForm = null;
        String primaryKey = null;
        File srcPDF = null;
        File srcImg = null;

        if (formDefIdSourcePdf != null && formDefIdSourceImg != null && formDefIdOutputPdf != null) {
            try {
                FormData formData = new FormData();

                // get pdf
                formData.setPrimaryKeyValue(recordIdSourcePdf);
                loadForm = appService.viewDataForm(appDef.getId(), appDef.getVersion().toString(), formDefIdSourcePdf, null, null, null, formData, null, null);
                Element el = FormUtil.findElement(fieldIdSourcePdf, loadForm, formData);
                srcPDF = FileUtil.getFile(FormUtil.getElementPropertyValue(el, formData), loadForm, recordIdSourcePdf);

                String filePaths = srcPDF.getPath();
                FormRowSet frs = new FormRowSet();

                List<String> fileList = getFilesList(filePaths);
                StringBuilder resultBuilder = new StringBuilder();

                for (String filePath : fileList) {
                    File uploadedFile = new File(filePath.trim());
                    FileInputStream fileIS = new FileInputStream(uploadedFile);
                    byte[] srcPDFFileContent = fileIS.readAllBytes();
                    fileIS.close();

                    // get image
                    formData.setPrimaryKeyValue(recordIdSourceImg);
                    loadForm = appService.viewDataForm(appDef.getId(), appDef.getVersion().toString(), formDefIdSourceImg, null, null, null, formData, null, null);
                    Element elImg = FormUtil.findElement(fieldIdSourceImg, loadForm, formData);
                    srcImg = FileUtil.getFile(FormUtil.getElementPropertyValue(elImg, formData), loadForm, recordIdSourceImg);
                    FileInputStream fileISImg = new FileInputStream(srcImg);
                    byte[] srcImgFileContent = fileISImg.readAllBytes();
                    fileISImg.close();

                    // put image in pdf
                    byte[] outputPDFFileContent = addImagetoPDF(srcPDFFileContent, srcImgFileContent);

                    // output in new pdf
                    String fileNameWithOutExt = FilenameUtils.removeExtension(uploadedFile.getName());
                    String fileName = fileNameWithOutExt + "_withImage.pdf";
                    String tableName = appService.getFormTableName(appDef, formDefIdOutputPdf);
                    String path = FileUtil.getUploadPath(tableName, recordIdOutputPdf);
                    final File file = new File(path + fileName);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    FileUtils.writeByteArrayToFile(file, outputPDFFileContent);

                    if (resultBuilder.length() > 0) {
                        resultBuilder.append(";");
                    }
                    resultBuilder.append(fileName);
                }

                if (fileList.size() > 0) {
                    FormRow row = new FormRow();
                    row.put(fieldIdOutputPdf, resultBuilder.toString());
                    frs.add(row);
                    appService.storeFormData(appDef.getAppId(), appDef.getVersion().toString(), formDefIdOutputPdf, frs, recordIdOutputPdf);
                }

            } catch (Exception ex) {
                LogUtil.error(getClassName(), ex, ex.getMessage());
            }
        }
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