package io.swagger.codegen.languages;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.swagger.codegen.CliOption;
import io.swagger.codegen.CodegenConstants;
import io.swagger.codegen.CodegenModel;
import io.swagger.codegen.CodegenOperation;
import io.swagger.codegen.CodegenProperty;
import io.swagger.codegen.CodegenType;
import io.swagger.codegen.SupportingFile;
import io.swagger.models.Operation;
import io.swagger.models.Swagger;
import io.swagger.util.Yaml;
import org.apache.commons.lang3.BooleanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CDSModelsCodegen extends AbstractJavaCodegen {

    private static final Logger LOGGER = LoggerFactory.getLogger(CDSModelsCodegen.class);

    protected String title = "CDS Models Generator";
    protected String implFolder = "src/gen/java";
    public CDSModelsCodegen() {
        super();

        sourceFolder = "src/gen/java";
        apiTestTemplateFiles.clear(); // TODO: add test template
        embeddedTemplateDir = templateDir = artifactId = "cds-models";

        // clear model and api doc templates
        modelDocTemplateFiles.remove("model_doc.mustache");
        apiDocTemplateFiles.remove("api_doc.mustache");
        
        // clear lots of far more complicated options we don't need
        cliOptions.clear();
        cliOptions.add(new CliOption(CodegenConstants.MODEL_PACKAGE, CodegenConstants.MODEL_PACKAGE_DESC));
        cliOptions.add(new CliOption(CodegenConstants.API_PACKAGE, CodegenConstants.API_PACKAGE_DESC));      


        apiPackage = System.getProperty("swagger.codegen.cdsmodels.apipackage", "au.org.consumerdatastandards.api");
        modelPackage = System.getProperty("swagger.codegen.cdsmodels.modelpackage", "au.org.consumerdatastandards.models");

        additionalProperties.put("title", title);
        // java inflector uses the jackson lib
        additionalProperties.put("jackson", "true");
    }

    @Override
    public CodegenType getTag() {
        return CodegenType.SERVER;
    }

    @Override
    public String getName() {
        return artifactId;
    }

    @Override
    public String getHelp() {
        return "Generates a Consumer Data Standards Models.";
    }

    @Override
    public void processOpts() {
        super.processOpts();
    }

    @Override
    public void addOperationToGroup(String tag, String resourcePath, Operation operation, CodegenOperation co, Map<String, List<CodegenOperation>> operations) {
        String basePath = resourcePath;
        if (basePath.startsWith("/")) {
            basePath = basePath.substring(1);
        }
        int pos = basePath.indexOf("/");
        if (pos > 0) {
            basePath = basePath.substring(0, pos);
        }

        if (basePath == "") {
            basePath = "default";
        } else {
            if (co.path.startsWith("/" + basePath)) {
                co.path = co.path.substring(("/" + basePath).length());
            }
            co.subresourceOperation = !co.path.isEmpty();
        }
        List<CodegenOperation> opList = operations.get(basePath);
        if (opList == null) {
            opList = new ArrayList<CodegenOperation>();
            operations.put(basePath, opList);
        }
        opList.add(co);
        co.baseName = basePath;
    }

    @Override
    public Map<String, Object> postProcessOperations(Map<String, Object> objs) {
        Map<String, Object> operations = (Map<String, Object>) objs.get("operations");
        if (operations != null) {
            List<CodegenOperation> ops = (List<CodegenOperation>) operations.get("operation");
            for (CodegenOperation operation : ops) {
                if (operation.returnType == null) {
                    operation.returnType = "Void";
                } else if (operation.returnType.startsWith("List")) {
                    String rt = operation.returnType;
                    int end = rt.lastIndexOf(">");
                    if (end > 0) {
                        operation.returnType = rt.substring("List<".length(), end);
                        operation.returnType = operation.returnType + "[]";
                        operation.returnContainer = "List";
                    }
                } else if (operation.returnType.startsWith("Map")) {
                    String rt = operation.returnType;
                    int end = rt.lastIndexOf(">");
                    if (end > 0) {
                        operation.returnType = rt.substring("Map<".length(), end);
                        operation.returnContainer = "Map";
                    }
                } else if (operation.returnType.startsWith("Set")) {
                    String rt = operation.returnType;
                    int end = rt.lastIndexOf(">");
                    if (end > 0) {
                        operation.returnType = rt.substring("Set<".length(), end);
                        operation.returnContainer = "Set";
                    }
                }
            }
        }
        return objs;
    }

    @Override
    public void postProcessModelProperty(CodegenModel model, CodegenProperty property) {
        super.postProcessModelProperty(model, property);

        //Add imports for Jackson
        if(!BooleanUtils.toBoolean(model.isEnum)) {
            model.imports.add("JsonProperty");

            if(BooleanUtils.toBoolean(model.hasEnums)) {
                model.imports.add("JsonValue");
            }
        }
    }

    @Override
    public Map<String, Object> postProcessModelsEnum(Map<String, Object> objs) {
        objs = super.postProcessModelsEnum(objs);

        //Add imports for Jackson
        List<Map<String, String>> imports = (List<Map<String, String>>)objs.get("imports");
        List<Object> models = (List<Object>) objs.get("models");
        for (Object _mo : models) {
            Map<String, Object> mo = (Map<String, Object>) _mo;
            CodegenModel cm = (CodegenModel) mo.get("model");
            // for enum model
            if (Boolean.TRUE.equals(cm.isEnum) && cm.allowableValues != null) {
                cm.imports.add(importMapping.get("JsonValue"));
                Map<String, String> item = new HashMap<String, String>();
                item.put("import", importMapping.get("JsonValue"));
                imports.add(item);
            }
        }

        return objs;
    }

    @Override
    protected String getOrGenerateOperationId(Operation operation, String path, String httpMethod) {
        return super.getOrGenerateOperationId(operation, path, httpMethod.toUpperCase());
    }

    public String apiFilename(String templateName, String tag) {
        String result = super.apiFilename(templateName, tag);

        if ( templateName.endsWith("api.mustache") ) {
            int ix = result.indexOf(sourceFolder);
            String beg = result.substring(0, ix);
            String end = result.substring(ix + sourceFolder.length());
            new java.io.File(beg + implFolder).mkdirs();
            result = beg + implFolder + end;
        }
        return result;
    }

    @Override
    public Map<String, Object> postProcessSupportingFileData(Map<String, Object> objs) {
        Swagger swagger = (Swagger)objs.get("swagger");
        if(swagger != null) {
            try {
                objs.put("swagger-yaml", Yaml.mapper().writeValueAsString(swagger));
            } catch (JsonProcessingException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
        return super.postProcessSupportingFileData(objs);
    }

    @Override
    public String toApiName(String name) {
        if (name.length() == 0) {
            return "DefaultController";
        }
        name = name.replaceAll("[^a-zA-Z0-9]+", "_");
        return camelize(name)+ "Controller";
    }
}
