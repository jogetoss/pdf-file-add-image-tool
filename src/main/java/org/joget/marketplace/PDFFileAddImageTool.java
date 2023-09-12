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

public class PDFFileAddImageTool extends DefaultApplicationPlugin{
    private final static String MESSAGE_PATH = "messages/PDFFileAddImageTool";

    @Override
    public String getName() {
        return AppPluginUtil.getMessage("org.joget.marketplace.PDFFileAddImageTool.pluginLabel", getClassName(), MESSAGE_PATH);
    }

    @Override
    public String getVersion() {
        return "7.0.0";
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

        String formDefId_SourcePDF = (String) map.get("formDefId_SourcePDF");
        String formDefId_SourceIMG = (String) map.get("formDefId_SourceIMG");
        String formDefId_OutputPDF = (String) map.get("formDefId_OutputPDF");
        String fieldId_SourcePDF = (String) map.get("fieldId_SourcePDF");
        String fieldId_SourceIMG = (String) map.get("fieldId_SourceIMG");
        String fieldId_OutputPDF = (String) map.get("fieldId_OutputPDF");
        String recordId_SourcePDF = (String) map.get("recordId_SourcePDF");
        String recordId_SourceIMG = (String) map.get("recordId_SourceIMG");
        String recordId_OutputPDF = (String) map.get("recordId_OutputPDF");

        WorkflowAssignment wfAssignment = (WorkflowAssignment) map.get("workflowAssignment");
        if (wfAssignment != null) {
            if(recordId_SourcePDF.equals("")) {
                recordId_SourcePDF = appService.getOriginProcessId(wfAssignment.getProcessId());
            } 
            if(recordId_SourceIMG.equals("")) {
                recordId_SourceIMG = appService.getOriginProcessId(wfAssignment.getProcessId());
            }
            if(recordId_OutputPDF.equals("")) {
                recordId_OutputPDF = appService.getOriginProcessId(wfAssignment.getProcessId());
            }
        }

        Form loadForm = null;
        String primaryKey = null;
        File srcPDF = null;
        File srcImg = null;

        if (formDefId_SourcePDF != null && formDefId_SourceIMG !=null && formDefId_OutputPDF !=null) {
            try {
                FormData formData = new FormData();

                // get pdf
                formData.setPrimaryKeyValue(recordId_SourcePDF);
                loadForm = appService.viewDataForm(appDef.getId(), appDef.getVersion().toString(), formDefId_SourcePDF, null, null, null, formData, null, null);
                Element el = FormUtil.findElement(fieldId_SourcePDF, loadForm, formData);
                srcPDF = FileUtil.getFile(FormUtil.getElementPropertyValue(el, formData), loadForm, recordId_SourcePDF);
                FileInputStream fileIS = new FileInputStream(srcPDF);
                byte[] srcPDFFileContent = fileIS.readAllBytes();
                fileIS.close();

                // get image
                formData.setPrimaryKeyValue(recordId_SourceIMG);
                loadForm = appService.viewDataForm(appDef.getId(), appDef.getVersion().toString(), formDefId_SourceIMG, null, null, null, formData, null, null);
                Element elImg = FormUtil.findElement(fieldId_SourceIMG, loadForm, formData);
                srcImg = FileUtil.getFile(FormUtil.getElementPropertyValue(elImg, formData), loadForm, recordId_SourceIMG);
                FileInputStream fileISImg = new FileInputStream(srcImg);
                byte[] srcImgFileContent = fileISImg.readAllBytes();
                fileISImg.close();
        
                // put image in pdf
                byte[] outputPDFFileContent = addImagetoPDF(srcPDFFileContent, srcImgFileContent);
          
                // output in new pdf
                String fileNameWithOutExt = FilenameUtils.removeExtension(srcPDF.getName());
                String fileName = fileNameWithOutExt + "_withImage.pdf";
                String tableName = appService.getFormTableName(appDef, formDefId_OutputPDF);
                String path = FileUtil.getUploadPath(tableName, recordId_OutputPDF);
                final File file = new File(path + fileName);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                FileUtils.writeByteArrayToFile(file, outputPDFFileContent);
        
                FormRowSet set = new FormRowSet();
                FormRow r1 = new FormRow();
                r1.put(fieldId_OutputPDF, fileName);
                set.add(r1);
                appService.storeFormData(appDef.getAppId(), appDef.getVersion().toString(), formDefId_OutputPDF, set, recordId_OutputPDF);
            } catch (Exception ex) {
                LogUtil.error(getClassName(), ex, ex.getMessage());
            }
        }
        return null;
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


