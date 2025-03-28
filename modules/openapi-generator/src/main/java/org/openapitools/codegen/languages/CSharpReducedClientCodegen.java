/*
 * Copyright 2018 OpenAPI-Generator Contributors (https://openapi-generator.tech)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openapitools.codegen.languages;

import com.google.common.collect.ImmutableMap;
import com.samskivert.mustache.Mustache;
import io.swagger.v3.oas.models.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.openapitools.codegen.*;
import org.openapitools.codegen.meta.features.*;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.utils.ModelUtils;
import org.openapitools.codegen.utils.ProcessUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.openapitools.codegen.utils.CamelizeOption.LOWERCASE_FIRST_LETTER;
import static org.openapitools.codegen.utils.StringUtils.camelize;
import static org.openapitools.codegen.utils.StringUtils.underscore;

@SuppressWarnings("Duplicates")
public class CSharpReducedClientCodegen extends AbstractCSharpCodegen {
    // Defines the sdk option for targeted frameworks, which differs from targetFramework and targetFrameworkNuget
    protected static final String MCS_NET_VERSION_KEY = "x-mcs-sdk";
    protected static final String SUPPORTS_UWP = "supportsUWP";
    protected static final String SUPPORTS_RETRY = "supportsRetry";

    protected static final String NET_STANDARD = "netStandard";

    // HTTP libraries
    protected static final String RESTSHARP = "restsharp";
    protected static final String HTTPCLIENT = "httpclient";

    // Project Variable, determined from target framework. Not intended to be user-settable.
    protected static final String TARGET_FRAMEWORK_IDENTIFIER = "targetFrameworkIdentifier";
    // Project Variable, determined from target framework. Not intended to be user-settable.
    protected static final String TARGET_FRAMEWORK_VERSION = "targetFrameworkVersion";

    @SuppressWarnings("hiding")
    private final Logger LOGGER = LoggerFactory.getLogger(CSharpReducedClientCodegen.class);
    private static final List<FrameworkStrategy> frameworkStrategies = Arrays.asList(
            FrameworkStrategy.NETSTANDARD_1_3,
            FrameworkStrategy.NETSTANDARD_1_4,
            FrameworkStrategy.NETSTANDARD_1_5,
            FrameworkStrategy.NETSTANDARD_1_6,
            FrameworkStrategy.NETSTANDARD_2_0,
            FrameworkStrategy.NETSTANDARD_2_1,
            FrameworkStrategy.NETCOREAPP_2_0,
            FrameworkStrategy.NETCOREAPP_2_1,
            FrameworkStrategy.NETFRAMEWORK_4_7,
            FrameworkStrategy.NET_5_0
    );
    private static FrameworkStrategy defaultFramework = FrameworkStrategy.NETSTANDARD_2_0;
    protected final Map<String, String> frameworks;
    @Setter protected String packageGuid = "{" + java.util.UUID.randomUUID().toString().toUpperCase(Locale.ROOT) + "}";
    protected String clientPackage = "Org.OpenAPITools.Client";
    protected String apiDocPath = "docs/";
    protected String modelDocPath = "docs/";

    // Defines TargetFrameworkVersion in csproj files
    protected String targetFramework = defaultFramework.name;
    protected String testTargetFramework = defaultFramework.testTargetFramework;

    // Defines nuget identifiers for target framework
    protected String targetFrameworkNuget = targetFramework;

    protected boolean supportsRetry = Boolean.TRUE;
    protected boolean supportsAsync = Boolean.TRUE;
    protected boolean netStandard = Boolean.FALSE;

    @Setter protected boolean validatable = Boolean.TRUE;
    protected Map<Character, String> regexModifiers;
    // By default, generated code is considered public
    @Getter @Setter
    protected boolean nonPublicApi = Boolean.FALSE;

    protected boolean caseInsensitiveResponseHeaders = Boolean.FALSE;
    protected String releaseNote = "Minor update";
    @Setter protected String licenseId;
    @Setter protected String packageTags;
    @Setter protected boolean useOneOfDiscriminatorLookup = false; // use oneOf discriminator's mapping for model lookup

    protected boolean needsCustomHttpMethod = false;
    protected boolean needsUriBuilder = false;

    public CSharpReducedClientCodegen() {
        super();

        modifyFeatureSet(features -> features
                .includeDocumentationFeatures(DocumentationFeature.Readme)
                .securityFeatures(EnumSet.of(
                        SecurityFeature.OAuth2_Implicit,
                        SecurityFeature.BasicAuth,
                        SecurityFeature.BearerToken,
                        SecurityFeature.ApiKey,
                        SecurityFeature.SignatureAuth
                ))
                .excludeGlobalFeatures(
                        GlobalFeature.XMLStructureDefinitions,
                        GlobalFeature.Callbacks,
                        GlobalFeature.LinkObjects,
                        GlobalFeature.ParameterStyling
                )
                .includeSchemaSupportFeatures(
                        SchemaSupportFeature.Polymorphism
                )
                .includeParameterFeatures(
                        ParameterFeature.Cookie
                )
                .includeClientModificationFeatures(
                        ClientModificationFeature.BasePath,
                        ClientModificationFeature.UserAgent
                )
        );

        setSupportNullable(Boolean.TRUE);
        hideGenerationTimestamp = Boolean.TRUE;
        supportsInheritance = true;
        modelTemplateFiles.put("model.mustache", ".cs");
        apiTemplateFiles.put("api.mustache", ".cs");
        modelDocTemplateFiles.put("model_doc.mustache", ".md");
        apiDocTemplateFiles.put("api_doc.mustache", ".md");
        embeddedTemplateDir = templateDir = "csharp-netcore";

        cliOptions.clear();

        // CLI options
        addOption(CodegenConstants.PACKAGE_NAME,
                "C# package name (convention: Title.Case).",
                this.packageName);

        addOption(CodegenConstants.PACKAGE_VERSION,
                "C# package version.",
                this.packageVersion);

        addOption(CodegenConstants.SOURCE_FOLDER,
                CodegenConstants.SOURCE_FOLDER_DESC,
                sourceFolder);

        addOption(CodegenConstants.OPTIONAL_PROJECT_GUID,
                CodegenConstants.OPTIONAL_PROJECT_GUID_DESC,
                null);

        addOption(CodegenConstants.INTERFACE_PREFIX,
                CodegenConstants.INTERFACE_PREFIX_DESC,
                interfacePrefix);

        addOption(CodegenConstants.LICENSE_ID,
                CodegenConstants.LICENSE_ID_DESC,
                this.licenseId);

        addOption(CodegenConstants.RELEASE_NOTE,
                CodegenConstants.RELEASE_NOTE_DESC,
                this.releaseNote);

        addOption(CodegenConstants.PACKAGE_TAGS,
                CodegenConstants.PACKAGE_TAGS_DESC,
                this.packageTags);

        CliOption framework = new CliOption(
                CodegenConstants.DOTNET_FRAMEWORK,
                CodegenConstants.DOTNET_FRAMEWORK_DESC
        );

        CliOption disallowAdditionalPropertiesIfNotPresentOpt = CliOption.newBoolean(
                CodegenConstants.DISALLOW_ADDITIONAL_PROPERTIES_IF_NOT_PRESENT,
                CodegenConstants.DISALLOW_ADDITIONAL_PROPERTIES_IF_NOT_PRESENT_DESC).defaultValue(Boolean.TRUE.toString());
        Map<String, String> disallowAdditionalPropertiesIfNotPresentOpts = new HashMap<>();
        disallowAdditionalPropertiesIfNotPresentOpts.put("false",
                "The 'additionalProperties' implementation is compliant with the OAS and JSON schema specifications.");
        disallowAdditionalPropertiesIfNotPresentOpts.put("true",
                "Keep the old (incorrect) behaviour that 'additionalProperties' is set to false by default.");
        disallowAdditionalPropertiesIfNotPresentOpt.setEnum(disallowAdditionalPropertiesIfNotPresentOpts);
        cliOptions.add(disallowAdditionalPropertiesIfNotPresentOpt);
        this.setDisallowAdditionalPropertiesIfNotPresent(true);

        ImmutableMap.Builder<String, String> frameworkBuilder = new ImmutableMap.Builder<>();
        for (FrameworkStrategy frameworkStrategy : frameworkStrategies) {
            frameworkBuilder.put(frameworkStrategy.name, frameworkStrategy.description);
        }

        frameworks = frameworkBuilder.build();

        framework.defaultValue(this.targetFramework);
        framework.setEnum(frameworks);
        cliOptions.add(framework);

        CliOption modelPropertyNaming = new CliOption(CodegenConstants.MODEL_PROPERTY_NAMING, CodegenConstants.MODEL_PROPERTY_NAMING_DESC);
        cliOptions.add(modelPropertyNaming.defaultValue("PascalCase"));

        // CLI Switches
        addSwitch(CodegenConstants.NULLABLE_REFERENCE_TYPES,
                CodegenConstants.NULLABLE_REFERENCE_TYPES_DESC,
                this.nullReferenceTypesFlag);

        addSwitch(CodegenConstants.HIDE_GENERATION_TIMESTAMP,
                CodegenConstants.HIDE_GENERATION_TIMESTAMP_DESC,
                this.hideGenerationTimestamp);

        addSwitch(CodegenConstants.SORT_PARAMS_BY_REQUIRED_FLAG,
                CodegenConstants.SORT_PARAMS_BY_REQUIRED_FLAG_DESC,
                this.sortParamsByRequiredFlag);

        addSwitch(CodegenConstants.USE_DATETIME_OFFSET,
                CodegenConstants.USE_DATETIME_OFFSET_DESC,
                this.useDateTimeOffsetFlag);

        addSwitch(CodegenConstants.USE_COLLECTION,
                CodegenConstants.USE_COLLECTION_DESC,
                this.useCollection);

        addSwitch(CodegenConstants.RETURN_ICOLLECTION,
                CodegenConstants.RETURN_ICOLLECTION_DESC,
                this.returnICollection);

        addSwitch(CodegenConstants.OPTIONAL_METHOD_ARGUMENT,
                "C# Optional method argument, e.g. void square(int x=10) (.net 4.0+ only).",
                this.optionalMethodArgumentFlag);

        addSwitch(CodegenConstants.OPTIONAL_ASSEMBLY_INFO,
                CodegenConstants.OPTIONAL_ASSEMBLY_INFO_DESC,
                this.optionalAssemblyInfoFlag);

        addSwitch(CodegenConstants.OPTIONAL_EMIT_DEFAULT_VALUES,
                CodegenConstants.OPTIONAL_EMIT_DEFAULT_VALUES_DESC,
                this.optionalEmitDefaultValuesFlag);

        addSwitch(CodegenConstants.OPTIONAL_CONDITIONAL_SERIALIZATION,
                CodegenConstants.OPTIONAL_CONDITIONAL_SERIALIZATION_DESC,
                this.conditionalSerialization);

        addSwitch(CodegenConstants.OPTIONAL_PROJECT_FILE,
                CodegenConstants.OPTIONAL_PROJECT_FILE_DESC,
                this.optionalProjectFileFlag);

        // NOTE: This will reduce visibility of all public members in templates. Users can use InternalsVisibleTo
        // https://msdn.microsoft.com/en-us/library/system.runtime.compilerservices.internalsvisibletoattribute(v=vs.110).aspx
        // to expose to shared code if the generated code is not embedded into another project. Otherwise, users of codegen
        // should rely on default public visibility.
        addSwitch(CodegenConstants.NON_PUBLIC_API,
                CodegenConstants.NON_PUBLIC_API_DESC,
                this.nonPublicApi);

        addSwitch(CodegenConstants.ALLOW_UNICODE_IDENTIFIERS,
                CodegenConstants.ALLOW_UNICODE_IDENTIFIERS_DESC,
                this.allowUnicodeIdentifiers);

        addSwitch(CodegenConstants.NETCORE_PROJECT_FILE,
                CodegenConstants.NETCORE_PROJECT_FILE_DESC,
                this.netCoreProjectFileFlag);

        addSwitch(CodegenConstants.VALIDATABLE,
                CodegenConstants.VALIDATABLE_DESC,
                this.validatable);

        addSwitch(CodegenConstants.USE_ONEOF_DISCRIMINATOR_LOOKUP,
                CodegenConstants.USE_ONEOF_DISCRIMINATOR_LOOKUP_DESC,
                this.caseInsensitiveResponseHeaders);

        addSwitch(CodegenConstants.CASE_INSENSITIVE_RESPONSE_HEADERS,
                CodegenConstants.CASE_INSENSITIVE_RESPONSE_HEADERS_DESC,
                this.caseInsensitiveResponseHeaders);

        regexModifiers = new HashMap<>();
        regexModifiers.put('i', "IgnoreCase");
        regexModifiers.put('m', "Multiline");
        regexModifiers.put('s', "Singleline");
        regexModifiers.put('x', "IgnorePatternWhitespace");

        supportedLibraries.put(HTTPCLIENT, "HttpClient (https://docs.microsoft.com/en-us/dotnet/api/system.net.http.httpclient) "
                + "(Experimental. May subject to breaking changes without further notice.)");
        supportedLibraries.put(RESTSHARP, "RestSharp (https://github.com/restsharp/RestSharp)");

        CliOption libraryOption = new CliOption(CodegenConstants.LIBRARY, "HTTP library template (sub-template) to use");
        libraryOption.setEnum(supportedLibraries);
        // set RESTSHARP as the default
        libraryOption.setDefault(RESTSHARP);
        cliOptions.add(libraryOption);
        setLibrary(RESTSHARP);
    }

    @Deprecated
    @Override
    protected Set<String> getNullableTypes() {
        return new HashSet<>(Arrays.asList("decimal", "bool", "int", "uint", "long", "ulong", "float", "double",
                "DateTime", "DateTimeOffset", "Guid"));
    }

    @Override
    protected Set<String> getValueTypes() {
        return new HashSet<>(Arrays.asList("decimal", "bool", "int", "uint", "long", "ulong", "float", "double"));
    }

    @Override
    protected void setTypeMapping() {
        super.setTypeMapping();
        // mapped non-nullable type without ?
        typeMapping = new HashMap<String, String>();
        typeMapping.put("string", "string");
        typeMapping.put("binary", "byte[]");
        typeMapping.put("ByteArray", "byte[]");
        typeMapping.put("boolean", "bool");
        typeMapping.put("integer", "int");
        typeMapping.put("float", "float");
        typeMapping.put("long", "long");
        typeMapping.put("double", "double");
        typeMapping.put("number", "decimal");
        typeMapping.put("decimal", "decimal");
        typeMapping.put("DateTime", "DateTime");
        typeMapping.put("date", "DateTime");
        typeMapping.put("file", "System.IO.Stream");
        typeMapping.put("array", "List");
        typeMapping.put("list", "List");
        typeMapping.put("map", "Dictionary");
        typeMapping.put("object", "Object");
        typeMapping.put("UUID", "Guid");
        typeMapping.put("URI", "string");
        typeMapping.put("AnyType", "Object");

        if (HTTPCLIENT.equals(getLibrary())) {
            typeMapping.put("file", "FileParameter");
        }
    }

    @Override
    protected void patchProperty(Map<String, CodegenModel> enumRefs, CodegenModel model, CodegenProperty property) {
        super.patchProperty(enumRefs, model, property);

        if (!property.isContainer && (this.getNullableTypes().contains(property.dataType) || property.isEnum)) {
            property.vendorExtensions.put("x-csharp-value-type", true);
        }
    }

    @Override
    protected void updateCodegenParameterEnum(CodegenParameter parameter, CodegenModel model) {
        super.updateCodegenParameterEnumLegacy(parameter, model);

        if (!parameter.required && parameter.vendorExtensions.get("x-csharp-value-type") != null) { //optional
            parameter.dataType = parameter.dataType + "?";
        }
    }

    @Override
    public String apiDocFileFolder() {
        return (outputFolder + "/" + apiDocPath).replace('/', File.separatorChar);
    }

    @Override
    public String apiTestFileFolder() {
        return outputFolder + File.separator + testFolder + File.separator + testPackageName() + File.separator + apiPackage();
    }

    @Override
    public CodegenModel fromModel(String name, Schema model) {
        Map<String, Schema> allDefinitions = ModelUtils.getSchemas(this.openAPI);
        CodegenModel codegenModel = super.fromModel(name, model);
        if (allDefinitions != null && codegenModel != null && codegenModel.parent != null) {
            final Schema parentModel = allDefinitions.get(toModelName(codegenModel.parent));
            if (parentModel != null) {
                final CodegenModel parentCodegenModel = super.fromModel(codegenModel.parent, parentModel);
                if (codegenModel.hasEnums) {
                    codegenModel = this.reconcileInlineEnums(codegenModel, parentCodegenModel);
                }

                Map<String, CodegenProperty> propertyHash = new HashMap<>(codegenModel.vars.size());
                for (final CodegenProperty property : codegenModel.vars) {
                    propertyHash.put(property.name, property);
                }

                for (final CodegenProperty property : codegenModel.readWriteVars) {
                    if (property.defaultValue == null && parentCodegenModel.discriminator != null && property.name.equals(parentCodegenModel.discriminator.getPropertyName())) {
                        property.defaultValue = "\"" + name + "\"";
                    }
                }

                CodegenProperty last = null;
                for (final CodegenProperty property : parentCodegenModel.vars) {
                    // helper list of parentVars simplifies templating
                    if (!propertyHash.containsKey(property.name)) {
                        final CodegenProperty parentVar = property.clone();
                        parentVar.isInherited = true;
                        last = parentVar;
                        LOGGER.debug("adding parent variable {}", property.name);
                        codegenModel.parentVars.add(parentVar);
                    }
                }
            }
        }

        // Cleanup possible duplicates. Currently, readWriteVars can contain the same property twice. May or may not be isolated to C#.
        if (codegenModel != null && codegenModel.readWriteVars != null && codegenModel.readWriteVars.size() > 1) {
            int length = codegenModel.readWriteVars.size() - 1;
            for (int i = length; i > (length / 2); i--) {
                final CodegenProperty codegenProperty = codegenModel.readWriteVars.get(i);
                // If the property at current index is found earlier in the list, remove this last instance.
                if (codegenModel.readWriteVars.indexOf(codegenProperty) < i) {
                    codegenModel.readWriteVars.remove(i);
                }
            }
        }

        return codegenModel;
    }

    @Override
    public String getHelp() {
        return "Generates a C# client library (.NET Standard, .NET Core).";
    }

    public String getModelPropertyNaming() {
        return this.modelPropertyNaming;
    }

    public void setModelPropertyNaming(String naming) {
        if ("original".equals(naming) || "camelCase".equals(naming) ||
                "PascalCase".equals(naming) || "snake_case".equals(naming)) {
            this.modelPropertyNaming = naming;
        } else {
            throw new IllegalArgumentException("Invalid model property naming '" +
                    naming + "'. Must be 'original', 'camelCase', " +
                    "'PascalCase' or 'snake_case'");
        }
    }

    @Override
    public String getName() {
        return "csharp-netcore";
    }

    public String getNameUsingModelPropertyNaming(String name) {
        switch (CodegenConstants.MODEL_PROPERTY_NAMING_TYPE.valueOf(getModelPropertyNaming())) {
            case original:
                return name;
            case camelCase:
                return camelize(name, LOWERCASE_FIRST_LETTER);
            case PascalCase:
                return camelize(name);
            case snake_case:
                return underscore(name);
            default:
                throw new IllegalArgumentException("Invalid model property naming '" +
                        name + "'. Must be 'original', 'camelCase', " +
                        "'PascalCase' or 'snake_case'");
        }
    }

    @Override
    public String getNullableType(Schema p, String type) {
        if (languageSpecificPrimitives.contains(type)) {
            if (isSupportNullable() && ModelUtils.isNullable(p) && this.getNullableTypes().contains(type)) {
                return type + "?";
            } else {
                return type;
            }
        } else {
            return null;
        }
    }

    @Override
    public CodegenType getTag() {
        return CodegenType.CLIENT;
    }

    @Override
    public String modelDocFileFolder() {
        return (outputFolder + "/" + modelDocPath).replace('/', File.separatorChar);
    }

    @Override
    public String modelTestFileFolder() {
        return outputFolder + File.separator + testFolder + File.separator + testPackageName() + File.separator + modelPackage();
    }

    @Override
    public void postProcessModelProperty(CodegenModel model, CodegenProperty property) {
        postProcessPattern(property.pattern, property.vendorExtensions);
        postProcessEmitDefaultValue(property.vendorExtensions);

        super.postProcessModelProperty(model, property);
    }

    @Override
    public void postProcessParameter(CodegenParameter parameter) {
        super.postProcessParameter(parameter);
        postProcessEmitDefaultValue(parameter.vendorExtensions);

        if (!parameter.dataType.endsWith("?") && !parameter.required && (nullReferenceTypesFlag || this.getNullableTypes().contains(parameter.dataType))) {
            parameter.dataType = parameter.dataType + "?";
        }
    }

    public void postProcessEmitDefaultValue(Map<String, Object> vendorExtensions) {
        vendorExtensions.put("x-emit-default-value", optionalEmitDefaultValuesFlag);
    }

    @Override
    public Mustache.Compiler processCompiler(Mustache.Compiler compiler) {
        // To avoid unexpected behaviors when options are passed programmatically such as { "supportsAsync": "" }
        return super.processCompiler(compiler).emptyStringIsFalse(true);
    }

    @Override
    public void processOpts() {
        this.setLegacyDiscriminatorBehavior(false);

        super.processOpts();

        /*
         * NOTE: When supporting boolean additionalProperties, you should read the value and write it back as a boolean.
         * This avoids oddities where additionalProperties contains "false" rather than false, which will cause the
         * templating engine to behave unexpectedly.
         *
         * Use the pattern:
         *     if (additionalProperties.containsKey(prop)) convertPropertyToBooleanAndWriteBack(prop);
         */

        if (additionalProperties.containsKey(CodegenConstants.DISALLOW_ADDITIONAL_PROPERTIES_IF_NOT_PRESENT)) {
            this.setDisallowAdditionalPropertiesIfNotPresent(Boolean.parseBoolean(additionalProperties
                    .get(CodegenConstants.DISALLOW_ADDITIONAL_PROPERTIES_IF_NOT_PRESENT).toString()));
        }

        if (additionalProperties.containsKey(CodegenConstants.OPTIONAL_EMIT_DEFAULT_VALUES)) {
            setOptionalEmitDefaultValuesFlag(convertPropertyToBooleanAndWriteBack(CodegenConstants.OPTIONAL_EMIT_DEFAULT_VALUES));
        } else {
            additionalProperties.put(CodegenConstants.OPTIONAL_EMIT_DEFAULT_VALUES, optionalEmitDefaultValuesFlag);
        }

        if (additionalProperties.containsKey(CodegenConstants.OPTIONAL_CONDITIONAL_SERIALIZATION)) {
            setConditionalSerialization(convertPropertyToBooleanAndWriteBack(CodegenConstants.OPTIONAL_CONDITIONAL_SERIALIZATION));
        } else {
            additionalProperties.put(CodegenConstants.OPTIONAL_CONDITIONAL_SERIALIZATION, conditionalSerialization);
        }

        if (additionalProperties.containsKey(CodegenConstants.MODEL_PROPERTY_NAMING)) {
            setModelPropertyNaming((String) additionalProperties.get(CodegenConstants.MODEL_PROPERTY_NAMING));
        }

        if (isEmpty(apiPackage)) {
            setApiPackage("Api");
        }
        if (isEmpty(modelPackage)) {
            setModelPackage("Model");
        }

        clientPackage = "Client";

        if (RESTSHARP.equals(getLibrary())) {
            additionalProperties.put("useRestSharp", true);
            needsCustomHttpMethod = true;
        } else if (HTTPCLIENT.equals(getLibrary())) {
            setLibrary(HTTPCLIENT);
            additionalProperties.put("useHttpClient", true);
            needsUriBuilder = true;
        } else {
            throw new RuntimeException("Invalid HTTP library " + getLibrary() + ". Only restsharp, httpclient are supported.");
        }

        String inputFramework = (String) additionalProperties.getOrDefault(CodegenConstants.DOTNET_FRAMEWORK, defaultFramework.name);
        String[] frameworks;
        List<FrameworkStrategy> strategies = new ArrayList<>();

        if (inputFramework.contains(";")) {
            // multiple target framework
            frameworks = inputFramework.split(";");
            additionalProperties.put("multiTarget", true);
        } else {
            // just a single value
            frameworks = new String[]{inputFramework};
        }

        for (String framework : frameworks) {
            boolean strategyMatched = false;
            for (FrameworkStrategy frameworkStrategy : frameworkStrategies) {
                if (framework.equals(frameworkStrategy.name)) {
                    strategies.add(frameworkStrategy);
                    strategyMatched = true;
                }

                if (frameworkStrategy != FrameworkStrategy.NETSTANDARD_2_0 && "restsharp".equals(getLibrary())) {
                    LOGGER.warn("If using built-in templates, RestSharp only supports netstandard 2.0 or later.");
                }
            }

            if (!strategyMatched) {
                // throws exception if the input targetFramework is invalid
                throw new IllegalArgumentException("The input (" + inputFramework + ") contains Invalid .NET framework version: " +
                        framework + ". List of supported versions: " +
                        frameworkStrategies.stream()
                                .map(p -> p.name)
                                .collect(Collectors.joining(", ")));
            }
        }

        configureAdditionalPropertiesForFrameworks(additionalProperties, strategies);
        setTargetFrameworkNuget(strategies);
        setTargetFramework(strategies);
        setTestTargetFramework(strategies);

        setSupportsAsync(Boolean.TRUE);
        setNetStandard(strategies.stream().anyMatch(p -> Boolean.TRUE.equals(p.isNetStandard)));

        if (!netStandard) {
            setNetCoreProjectFileFlag(true);
        }

        if (additionalProperties.containsKey(CodegenConstants.GENERATE_PROPERTY_CHANGED)) {
            LOGGER.warn("{} is not supported in the .NET Standard generator.", CodegenConstants.GENERATE_PROPERTY_CHANGED);
            additionalProperties.remove(CodegenConstants.GENERATE_PROPERTY_CHANGED);
        }

        final AtomicReference<Boolean> excludeTests = new AtomicReference<>();
        syncBooleanProperty(additionalProperties, CodegenConstants.EXCLUDE_TESTS, excludeTests::set, false);

        syncStringProperty(additionalProperties, "clientPackage", (s) -> {
        }, clientPackage);

        syncStringProperty(additionalProperties, CodegenConstants.API_PACKAGE, this::setApiPackage, apiPackage);
        syncStringProperty(additionalProperties, CodegenConstants.MODEL_PACKAGE, this::setModelPackage, modelPackage);
        syncStringProperty(additionalProperties, CodegenConstants.OPTIONAL_PROJECT_GUID, this::setPackageGuid, packageGuid);
        syncStringProperty(additionalProperties, "targetFrameworkNuget", this::setTargetFrameworkNuget, this.targetFrameworkNuget);
        syncStringProperty(additionalProperties, "testTargetFramework", this::setTestTargetFramework, this.testTargetFramework);

        syncBooleanProperty(additionalProperties, "netStandard", this::setNetStandard, this.netStandard);

        syncBooleanProperty(additionalProperties, CodegenConstants.VALIDATABLE, this::setValidatable, this.validatable);
        syncBooleanProperty(additionalProperties, CodegenConstants.SUPPORTS_ASYNC, this::setSupportsAsync, this.supportsAsync);
        syncBooleanProperty(additionalProperties, SUPPORTS_RETRY, this::setSupportsRetry, this.supportsRetry);
        syncBooleanProperty(additionalProperties, CodegenConstants.OPTIONAL_METHOD_ARGUMENT, this::setOptionalMethodArgumentFlag, optionalMethodArgumentFlag);
        syncBooleanProperty(additionalProperties, CodegenConstants.NON_PUBLIC_API, this::setNonPublicApi, isNonPublicApi());
        syncBooleanProperty(additionalProperties, CodegenConstants.USE_ONEOF_DISCRIMINATOR_LOOKUP, this::setUseOneOfDiscriminatorLookup, this.useOneOfDiscriminatorLookup);

        final String testPackageName = testPackageName();
        String packageFolder = sourceFolder + File.separator + packageName;
        String clientPackageDir = packageFolder + File.separator + clientPackage;
        String modelPackageDir = packageFolder + File.separator + modelPackage;
        String testPackageFolder = testFolder + File.separator + testPackageName;

        additionalProperties.put("testPackageName", testPackageName);

        //Compute the relative path to the bin directory where the external assemblies live
        //This is necessary to properly generate the project file
        int packageDepth = packageFolder.length() - packageFolder.replace(java.io.File.separator, "").length();
        String binRelativePath = "..\\";
        for (int i = 0; i < packageDepth; i = i + 1) {
            binRelativePath += "..\\";
        }
        binRelativePath += "vendor";
        additionalProperties.put("binRelativePath", binRelativePath);

        if (HTTPCLIENT.equals(getLibrary())) {
            supportingFiles.add(new SupportingFile("FileParameter.mustache", clientPackageDir, "FileParameter.cs"));
        }

        supportingFiles.add(new SupportingFile("IApiAccessor.mustache", clientPackageDir, "IApiAccessor.cs"));
        supportingFiles.add(new SupportingFile("Configuration.mustache", clientPackageDir, "Configuration.cs"));
        supportingFiles.add(new SupportingFile("ApiClient.mustache", clientPackageDir, "ApiClient.cs"));
        supportingFiles.add(new SupportingFile("ApiException.mustache", clientPackageDir, "ApiException.cs"));
        supportingFiles.add(new SupportingFile("ApiResponse.mustache", clientPackageDir, "ApiResponse.cs"));
        supportingFiles.add(new SupportingFile("ExceptionFactory.mustache", clientPackageDir, "ExceptionFactory.cs"));
        supportingFiles.add(new SupportingFile("OpenAPIDateConverter.mustache", clientPackageDir, "OpenAPIDateConverter.cs"));
        supportingFiles.add(new SupportingFile("ClientUtils.mustache", clientPackageDir, "ClientUtils.cs"));
        if (needsCustomHttpMethod) {
            supportingFiles.add(new SupportingFile("HttpMethod.mustache", clientPackageDir, "HttpMethod.cs"));
        }
        if (needsUriBuilder) {
            supportingFiles.add(new SupportingFile("WebRequestPathBuilder.mustache", clientPackageDir, "WebRequestPathBuilder.cs"));
        }
        if (ProcessUtils.hasHttpSignatureMethods(openAPI)) {
            supportingFiles.add(new SupportingFile("HttpSigningConfiguration.mustache", clientPackageDir, "HttpSigningConfiguration.cs"));
        }
        if (supportsAsync) {
            supportingFiles.add(new SupportingFile("IAsynchronousClient.mustache", clientPackageDir, "IAsynchronousClient.cs"));
        }
        supportingFiles.add(new SupportingFile("ISynchronousClient.mustache", clientPackageDir, "ISynchronousClient.cs"));
        supportingFiles.add(new SupportingFile("RequestOptions.mustache", clientPackageDir, "RequestOptions.cs"));
        supportingFiles.add(new SupportingFile("Multimap.mustache", clientPackageDir, "Multimap.cs"));

        if (supportsRetry) {
            supportingFiles.add(new SupportingFile("RetryConfiguration.mustache", clientPackageDir, "RetryConfiguration.cs"));
        }

        supportingFiles.add(new SupportingFile("IReadableConfiguration.mustache",
                clientPackageDir, "IReadableConfiguration.cs"));
        supportingFiles.add(new SupportingFile("GlobalConfiguration.mustache",
                clientPackageDir, "GlobalConfiguration.cs"));

        // Only write out test related files if excludeTests is unset or explicitly set to false (see start of this method)
        if (Boolean.FALSE.equals(excludeTests.get())) {
            modelTestTemplateFiles.put("model_test.mustache", ".cs");
            apiTestTemplateFiles.put("api_test.mustache", ".cs");
        }

        supportingFiles.add(new SupportingFile("README.mustache", "", "README.md"));
        supportingFiles.add(new SupportingFile("git_push.sh.mustache", "", "git_push.sh"));
        supportingFiles.add(new SupportingFile("gitignore.mustache", "", ".gitignore"));

        supportingFiles.add(new SupportingFile("appveyor.mustache", "", "appveyor.yml"));
        supportingFiles.add(new SupportingFile("AbstractOpenAPISchema.mustache", modelPackageDir, "AbstractOpenAPISchema.cs"));

        additionalProperties.put("apiDocPath", apiDocPath);
        additionalProperties.put("modelDocPath", modelDocPath);

        this.setTypeMapping();
    }

    public void setNetStandard(Boolean netStandard) {
        this.netStandard = netStandard;
    }

    public void setOptionalAssemblyInfoFlag(boolean flag) {
        this.optionalAssemblyInfoFlag = flag;
    }

    public void setOptionalEmitDefaultValuesFlag(boolean flag) {
        this.optionalEmitDefaultValuesFlag = flag;
    }

    public void setConditionalSerialization(boolean flag) {
        this.conditionalSerialization = flag;
    }

    public void setOptionalProjectFileFlag(boolean flag) {
        this.optionalProjectFileFlag = flag;
    }

    @Override
    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    @Override
    public void setPackageVersion(String packageVersion) {
        this.packageVersion = packageVersion;
    }

    public void setSupportsAsync(Boolean supportsAsync) {
        this.supportsAsync = supportsAsync;
    }

    public void setSupportsRetry(Boolean supportsRetry) {
        this.supportsRetry = supportsRetry;
    }

    public void setTargetFramework(String dotnetFramework) {
        if (!frameworks.containsKey(dotnetFramework)) {
            throw new IllegalArgumentException("Invalid .NET framework version: " +
                    dotnetFramework + ". List of supported versions: " +
                    frameworkStrategies.stream()
                            .map(p -> p.name)
                            .collect(Collectors.joining(", ")));
        } else {
            this.targetFramework = dotnetFramework;
        }
        LOGGER.info("Generating code for .NET Framework {}", this.targetFramework);
    }

    public void setTargetFramework(List<FrameworkStrategy> strategies) {
        for (FrameworkStrategy strategy : strategies) {
            if (!frameworks.containsKey(strategy.name)) {
                throw new IllegalArgumentException("Invalid .NET framework version: " +
                        strategy.name + ". List of supported versions: " +
                        frameworkStrategies.stream()
                                .map(p -> p.name)
                                .collect(Collectors.joining(", ")));
            }
        }
        this.targetFramework = strategies.stream().map(p -> p.name)
                .collect(Collectors.joining(";"));
        LOGGER.info("Generating code for .NET Framework {}", this.targetFramework);
    }

    public void setTestTargetFramework(String testTargetFramework) {
        this.testTargetFramework = testTargetFramework;
    }

    public void setTestTargetFramework(List<FrameworkStrategy> strategies) {
        this.testTargetFramework = strategies.stream().map(p -> p.testTargetFramework)
                .collect(Collectors.joining(";"));
    }

    public void setTargetFrameworkNuget(String targetFrameworkNuget) {
        this.targetFrameworkNuget = targetFrameworkNuget;
    }

    public void setTargetFrameworkNuget(List<FrameworkStrategy> strategies) {
        this.targetFrameworkNuget = strategies.stream().map(p -> p.getNugetFrameworkIdentifier())
                .collect(Collectors.joining(";"));
    }

    public void setCaseInsensitiveResponseHeaders(final Boolean caseInsensitiveResponseHeaders) {
        this.caseInsensitiveResponseHeaders = caseInsensitiveResponseHeaders;
    }

    @Override
    public void setReleaseNote(String releaseNote) {
        this.releaseNote = releaseNote;
    }

    public boolean getUseOneOfDiscriminatorLookup() {
        return this.useOneOfDiscriminatorLookup;
    }

    @Override
    public String toEnumVarName(String value, String datatype) {
        if (value.length() == 0) {
            return "Empty";
        }

        // for symbol, e.g. $, #
        if (getSymbolName(value) != null) {
            return camelize(getSymbolName(value));
        }

        // number
        if (datatype.startsWith("int") || datatype.startsWith("long") ||
                datatype.startsWith("double") || datatype.startsWith("float")) {
            String varName = "NUMBER_" + value;
            varName = varName.replaceAll("-", "MINUS_");
            varName = varName.replaceAll("\\+", "PLUS_");
            varName = varName.replaceAll("\\.", "_DOT_");
            return varName;
        }

        // string
        String var = value.replaceAll(" ", "_");
        var = camelize(var);
        var = var.replaceAll("\\W+", "");

        if (var.matches("\\d.*")) {
            return "_" + var;
        } else {
            return var;
        }
    }

    @Override
    public String toModelDocFilename(String name) {
        return toModelFilename(name);
    }

    @Override
    public String toVarName(String name) {
        // obtain the name from nameMapping directly if provided
        if (nameMapping.containsKey(name)) {
            return nameMapping.get(name);
        }

        // sanitize name
        name = sanitizeName(name);

        // if it's all upper case, do nothing
        if (name.matches("^[A-Z_]*$")) {
            return name;
        }

        name = getNameUsingModelPropertyNaming(name);

        // for reserved word or word starting with number, append _
        if (isReservedWord(name) || name.matches("^\\d.*")) {
            name = escapeReservedWord(name);
        }

        // for function names in the model, escape with the "Property" prefix
        if (propertySpecialKeywords.contains(name)) {
            return camelize("property_" + name);
        }

        return name;
    }

    @Override
    protected void patchVendorExtensionNullableValueType(CodegenParameter parameter) {
        super.patchVendorExtensionNullableValueTypeLegacy(parameter);
    }

    private CodegenModel reconcileInlineEnums(CodegenModel codegenModel, CodegenModel parentCodegenModel) {
        // This generator uses inline classes to define enums, which breaks when
        // dealing with models that have subTypes. To clean this up, we will analyze
        // the parent and child models, look for enums that match, and remove
        // them from the child models and leave them in the parent.
        // Because the child models extend the parents, the enums will be available via the parent.

        // Only bother with reconciliation if the parent model has enums.
        if (parentCodegenModel.hasEnums) {

            // Get the properties for the parent and child models
            final List<CodegenProperty> parentModelCodegenProperties = parentCodegenModel.vars;
            List<CodegenProperty> codegenProperties = codegenModel.vars;

            // Iterate over all of the parent model properties
            boolean removedChildEnum = false;
            for (CodegenProperty parentModelCodegenProperty : parentModelCodegenProperties) {
                // Look for enums
                if (parentModelCodegenProperty.isEnum) {
                    // Now that we have found an enum in the parent class,
                    // and search the child class for the same enum.
                    Iterator<CodegenProperty> iterator = codegenProperties.iterator();
                    while (iterator.hasNext()) {
                        CodegenProperty codegenProperty = iterator.next();
                        if (codegenProperty.isEnum && codegenProperty.equals(parentModelCodegenProperty)) {
                            // We found an enum in the child class that is
                            // a duplicate of the one in the parent, so remove it.
                            iterator.remove();
                            removedChildEnum = true;
                        }
                    }
                }
            }

            if (removedChildEnum) {
                codegenModel.vars = codegenProperties;
            }
        }

        return codegenModel;
    }

    private void syncBooleanProperty(final Map<String, Object> additionalProperties, final String key, final Consumer<Boolean> setter, final Boolean defaultValue) {
        if (additionalProperties.containsKey(key)) {
            setter.accept(convertPropertyToBooleanAndWriteBack(key));
        } else {
            additionalProperties.put(key, defaultValue);
            setter.accept(defaultValue);
        }
    }

    private void syncStringProperty(final Map<String, Object> additionalProperties, final String key, final Consumer<String> setter, final String defaultValue) {
        if (additionalProperties.containsKey(key)) {
            setter.accept((String) additionalProperties.get(key));
        } else {
            additionalProperties.put(key, defaultValue);
            setter.accept(defaultValue);
        }
    }

    // https://docs.microsoft.com/en-us/dotnet/standard/net-standard
    @SuppressWarnings("Duplicates")
    private static abstract class FrameworkStrategy {

        private final Logger LOGGER = LoggerFactory.getLogger(CSharpReducedClientCodegen.class);

        static FrameworkStrategy NETSTANDARD_1_3 = new FrameworkStrategy("netstandard1.3", ".NET Standard 1.3 compatible", "netcoreapp2.0") {
        };
        static FrameworkStrategy NETSTANDARD_1_4 = new FrameworkStrategy("netstandard1.4", ".NET Standard 1.4 compatible", "netcoreapp2.0") {
        };
        static FrameworkStrategy NETSTANDARD_1_5 = new FrameworkStrategy("netstandard1.5", ".NET Standard 1.5 compatible", "netcoreapp2.0") {
        };
        static FrameworkStrategy NETSTANDARD_1_6 = new FrameworkStrategy("netstandard1.6", ".NET Standard 1.6 compatible", "netcoreapp2.0") {
        };
        static FrameworkStrategy NETSTANDARD_2_0 = new FrameworkStrategy("netstandard2.0", ".NET Standard 2.0 compatible", "netcoreapp2.0") {
        };
        static FrameworkStrategy NETSTANDARD_2_1 = new FrameworkStrategy("netstandard2.1", ".NET Standard 2.1 compatible", "netcoreapp3.0") {
        };
        static FrameworkStrategy NETCOREAPP_2_0 = new FrameworkStrategy("netcoreapp2.0", ".NET Core 2.0 compatible", "netcoreapp2.0", Boolean.FALSE) {
        };
        static FrameworkStrategy NETCOREAPP_2_1 = new FrameworkStrategy("netcoreapp2.1", ".NET Core 2.1 compatible", "netcoreapp2.1", Boolean.FALSE) {
        };
        static FrameworkStrategy NETFRAMEWORK_4_7 = new FrameworkStrategy("net47", ".NET Framework 4.7 compatible", "net47", Boolean.FALSE) {
        };
        static FrameworkStrategy NET_5_0 = new FrameworkStrategy("net5.0", ".NET 5.0 compatible", "net5.0", Boolean.FALSE) {
        };
        protected String name;
        protected String description;
        protected String testTargetFramework;
        private Boolean isNetStandard = Boolean.TRUE;

        FrameworkStrategy(String name, String description, String testTargetFramework) {
            this.name = name;
            this.description = description;
            this.testTargetFramework = testTargetFramework;
        }

        FrameworkStrategy(String name, String description, String testTargetFramework, Boolean isNetStandard) {
            this.name = name;
            this.description = description;
            this.testTargetFramework = testTargetFramework;
            this.isNetStandard = isNetStandard;
        }

        protected void configureAdditionalProperties(final Map<String, Object> properties) {
            properties.putIfAbsent(CodegenConstants.DOTNET_FRAMEWORK, this.name);

            // not intended to be user-settable
            properties.put(TARGET_FRAMEWORK_IDENTIFIER, this.getTargetFrameworkIdentifier());
            properties.put(TARGET_FRAMEWORK_VERSION, this.getTargetFrameworkVersion());
            properties.putIfAbsent(MCS_NET_VERSION_KEY, "4.6-api");

            properties.put(NET_STANDARD, this.isNetStandard);
            if (properties.containsKey(SUPPORTS_UWP)) {
                LOGGER.warn(".NET {} generator does not support the UWP option. Use the csharp generator instead.",
                        this.name);
                properties.remove(SUPPORTS_UWP);
            }
        }

        protected String getNugetFrameworkIdentifier() {
            return this.name.toLowerCase(Locale.ROOT);
        }

        protected String getTargetFrameworkIdentifier() {
            if (this.isNetStandard) return ".NETStandard";
            else return ".NETCoreApp";
        }

        protected String getTargetFrameworkVersion() {
            if (this.isNetStandard) return "v" + this.name.replace("netstandard", "");
            else return "v" + this.name.replace("netcoreapp", "");
        }
    }

    protected void configureAdditionalPropertiesForFrameworks(final Map<String, Object> properties, List<FrameworkStrategy> strategies) {
        properties.putIfAbsent(CodegenConstants.DOTNET_FRAMEWORK, strategies.stream()
                .map(p -> p.name)
                .collect(Collectors.joining(";")));

        // not intended to be user-settable
        properties.put(TARGET_FRAMEWORK_IDENTIFIER, strategies.stream()
                .map(p -> p.getTargetFrameworkIdentifier())
                .collect(Collectors.joining(";")));
        properties.put(TARGET_FRAMEWORK_VERSION, strategies.stream()
                .map(p -> p.getTargetFrameworkVersion())
                .collect(Collectors.joining(";")));
        properties.putIfAbsent(MCS_NET_VERSION_KEY, "4.6-api");

        properties.put(NET_STANDARD, strategies.stream().anyMatch(p -> Boolean.TRUE.equals(p.isNetStandard)));
    }

    /**
     * Return the instantiation type of the property, especially for map and array
     *
     * @param schema property schema
     * @return string presentation of the instantiation type of the property
     */
    @Override
    public String toInstantiationType(Schema schema) {
        if (ModelUtils.isMapSchema(schema)) {
            Schema additionalProperties = ModelUtils.getAdditionalProperties(schema);
            String inner = getSchemaType(additionalProperties);
            if (ModelUtils.isMapSchema(additionalProperties)) {
                inner = toInstantiationType(additionalProperties);
            }
            return instantiationTypes.get("map") + "<String, " + inner + ">";
        } else if (ModelUtils.isArraySchema(schema)) {
            String inner = getSchemaType(ModelUtils.getSchemaItems(schema));
            return instantiationTypes.get("array") + "<" + inner + ">";
        } else {
            return null;
        }
    }

    @Override
    public ModelsMap postProcessModels(ModelsMap objs) {
        objs = super.postProcessModels(objs);

        // add implements for serializable/parcelable to all models
        for (ModelMap mo : objs.getModels()) {
            CodegenModel cm = mo.getModel();

            if (cm.oneOf != null && !cm.oneOf.isEmpty() && cm.oneOf.contains("ModelNull")) {
                // if oneOf contains "null" type
                cm.isNullable = true;
                cm.oneOf.remove("ModelNull");
            }

            if (cm.anyOf != null && !cm.anyOf.isEmpty() && cm.anyOf.contains("ModelNull")) {
                // if anyOf contains "null" type
                cm.isNullable = true;
                cm.anyOf.remove("ModelNull");
            }
        }

        return objs;
    }

    @Override
    public void postProcess() {
        System.out.println("################################################################################");
        System.out.println("# Thanks for using OpenAPI Generator.                                          #");
        System.out.println("# Please consider donation to help us maintain this project \uD83D\uDE4F                 #");
        System.out.println("# https://opencollective.com/openapi_generator/donate                          #");
        System.out.println("#                                                                              #");
        System.out.println("# This generator's contributed by Jim Schubert (https://github.com/jimschubert)#");
        System.out.println("# Please support his work directly via https://patreon.com/jimschubert \uD83D\uDE4F      #");
        System.out.println("################################################################################");
    }

    @Override
    protected void updateModelForObject(CodegenModel m, Schema schema) {
        /**
         * we have a custom version of this function so we only set isMap to true if
         * ModelUtils.isMapSchema
         * In other generators, isMap is true for all type object schemas
         */
        if (schema.getProperties() != null || schema.getRequired() != null && !(ModelUtils.isComposedSchema(schema))) {
            // passing null to allProperties and allRequired as there's no parent
            addVars(m, unaliasPropertySchema(schema.getProperties()), schema.getRequired(), null, null);
        }
        if (ModelUtils.isMapSchema(schema)) {
            // an object or anyType composed schema that has additionalProperties set
            addAdditionPropertiesToCodeGenModel(m, schema);
        } else {
            m.setIsMap(false);
            if (ModelUtils.isFreeFormObject(schema, openAPI)) {
                // non-composed object type with no properties + additionalProperties
                // additionalProperties must be null, ObjectSchema, or empty Schema
                addAdditionPropertiesToCodeGenModel(m, schema);
            }
        }
        // process 'additionalProperties'
        setAddProps(schema, m);
    }
}
