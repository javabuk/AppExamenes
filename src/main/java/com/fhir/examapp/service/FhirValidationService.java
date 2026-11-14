package com.fhir.examapp.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fhir.examapp.dto.FhirResourceValidationResult;
import com.fhir.examapp.dto.FhirSearchValidationResult;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class FhirValidationService {

    private final FhirContext fhirContextR4;
    private final FhirContext fhirContextR5;

    public FhirValidationService() {
        this.fhirContextR4 = FhirContext.forR4();
        this.fhirContextR5 = FhirContext.forR5();
    }

    public FhirContext getFhirContext() {
        return fhirContextR4;
    }

    public FhirContext getFhirContextForVersion(String version) {
        if (version != null && (version.equalsIgnoreCase("R5") 
                || version.equalsIgnoreCase("FHIR_R5") 
                || version.startsWith("5."))) {
            return fhirContextR5;
        }
        return fhirContextR4;
    }

    /**
     * Valida un recurso JSON FHIR R4 por defecto.
     */
    public FhirResourceValidationResult validateResource(String resourceJson) {
        return validateResource(resourceJson, "R4");
    }

    /**
     * Valida un recurso JSON FHIR según la versión indicada (R4 o R5).
     */
    public FhirResourceValidationResult validateResource(String resourceJson, String version) {
        FhirResourceValidationResult result = new FhirResourceValidationResult();
        List<String> issues = new ArrayList<>();
        FhirContext ctx = getFhirContextForVersion(version);
        String verLabel = (ctx == fhirContextR5) ? "FHIR R5" : "FHIR R4";

        if (resourceJson == null || resourceJson.trim().isEmpty()) {
            result.setValid(false);
            issues.add("El cuerpo del recurso JSON está vacío.");
            result.setIssues(issues);
            return result;
        }

        try {
            IParser parser = ctx.newJsonParser().setPrettyPrint(true);
            IBaseResource resource = parser.parseResource(resourceJson);

            String resourceType = ctx.getResourceType(resource);
            String resourceId = resource.getIdElement() != null ? resource.getIdElement().getIdPart() : null;

            result.setResourceType(resourceType);
            result.setResourceId(resourceId);
            result.setFormattedJson(parser.encodeResourceToString(resource));

            // Comprobaciones semánticas comunes del examen HL7 FHIR
            checkCommonFhirRules(resourceType, resourceJson, issues, verLabel);

            result.setValid(issues.isEmpty());
            result.setIssues(issues);
            result.setSummary(issues.isEmpty() 
                ? "Recurso " + verLabel + " '" + resourceType + "' sintácticamente válido según HAPI FHIR."
                : "Recurso " + verLabel + " '" + resourceType + "' analizado con advertencias de conformidad.");

        } catch (Exception e) {
            result.setValid(false);
            issues.add("Error de análisis sintáctico HAPI FHIR (" + verLabel + "): " + e.getMessage());
            result.setIssues(issues);
            result.setSummary("El JSON proporcionado no cumple la especificación HL7 " + verLabel + ".");
        }

        return result;
    }

    private void checkCommonFhirRules(String resourceType, String json, List<String> issues, String verLabel) {
        // Reglas de examen FHIR comunes:
        if ("Observation".equalsIgnoreCase(resourceType)) {
            if (!json.contains("\"status\"")) {
                issues.add("Infracción de cardinalidad 1..1: Observation requiere obligatoriamente el elemento 'status'.");
            }
            if (!json.contains("\"code\"")) {
                issues.add("Infracción de cardinalidad 1..1: Observation requiere obligatoriamente el elemento 'code'.");
            }
        } else if ("Patient".equalsIgnoreCase(resourceType)) {
            // Patient no tiene campos 1..1 obligatorios, pero se verifica estructura
            if (json.contains("\"gender\"") && !json.matches("(?s).*\"gender\"\\s*:\\s*\"(male|female|other|unknown)\".*")) {
                issues.add("Valor de terminología inválido: 'gender' en Patient requiere binding de código cerrado (male | female | other | unknown).");
            }
        } else if ("Bundle".equalsIgnoreCase(resourceType)) {
            if (!json.contains("\"type\"")) {
                issues.add("Infracción de cardinalidad 1..1: Bundle requiere obligatoriamente el elemento 'type' (document, message, transaction, batch, searchset, collection...).");
            }
        } else if ("Encounter".equalsIgnoreCase(resourceType)) {
            if (!json.contains("\"status\"")) {
                issues.add("Infracción de cardinalidad 1..1: Encounter requiere obligatoriamente el elemento 'status'.");
            }
            if (!json.contains("\"class\"")) {
                issues.add("Infracción de cardinalidad 1..1: Encounter requiere obligatoriamente el elemento 'class'.");
            }
        }
    }

    /**
     * Valida y desglosa una consulta de búsqueda RESTful FHIR.
     * Ejemplo: GET [base]/Observation?status=final&code=http://loinc.org|8867-4&_include=Observation:subject
     */
    public FhirSearchValidationResult validateSearchQuery(String rawUrl, String expectedResourceType) {
        FhirSearchValidationResult result = new FhirSearchValidationResult();
        List<String> issues = new ArrayList<>();
        List<String> positiveFindings = new ArrayList<>();
        Map<String, List<String>> parsedParams = new LinkedHashMap<>();

        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            result.setValid(false);
            issues.add("La URL de búsqueda no puede estar vacía.");
            result.setIssues(issues);
            return result;
        }

        String cleanedUrl = rawUrl.trim();
        String httpMethod = "GET";

        if (cleanedUrl.toUpperCase().startsWith("GET ")) {
            httpMethod = "GET";
            cleanedUrl = cleanedUrl.substring(4).trim();
        } else if (cleanedUrl.toUpperCase().startsWith("POST ")) {
            httpMethod = "POST";
            cleanedUrl = cleanedUrl.substring(5).trim();
        }
        result.setHttpMethod(httpMethod);

        // Normalizar [base]
        cleanedUrl = cleanedUrl.replace("[base]/", "").replace("[base]", "").replace("http://example.org/fhir/", "");
        if (cleanedUrl.startsWith("/")) {
            cleanedUrl = cleanedUrl.substring(1);
        }

        String resourceTypePart = cleanedUrl;
        String queryString = "";
        int queryIndex = cleanedUrl.indexOf('?');
        if (queryIndex != -1) {
            resourceTypePart = cleanedUrl.substring(0, queryIndex);
            queryString = cleanedUrl.substring(queryIndex + 1);
        }

        // Limpiar subpath si es _search
        if (resourceTypePart.endsWith("/_search")) {
            resourceTypePart = resourceTypePart.substring(0, resourceTypePart.length() - 8);
            positiveFindings.add("Uso correcto de la operación estándar /_search");
        }

        result.setResourceType(resourceTypePart);

        if (expectedResourceType != null && !expectedResourceType.trim().isEmpty()) {
            if (!expectedResourceType.equalsIgnoreCase(resourceTypePart)) {
                issues.add("El tipo de recurso '" + resourceTypePart + "' no coincide con el recurso esperado '" + expectedResourceType + "'.");
            } else {
                positiveFindings.add("Recurso destino '" + resourceTypePart + "' verificado.");
            }
        }

        // Parsear parámetros de consulta
        if (!queryString.isEmpty()) {
            String[] pairs = queryString.split("&");
            for (String pair : pairs) {
                if (pair.isEmpty()) continue;
                int idx = pair.indexOf('=');
                String key = idx > 0 ? URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8) : pair;
                String value = idx > 0 && pair.length() > idx + 1 ? URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8) : "";

                parsedParams.computeIfAbsent(key, k -> new ArrayList<>()).add(value);

                // Validaciones de parámetros de búsqueda FHIR
                analyzeSearchParameter(key, value, resourceTypePart, issues, positiveFindings);
            }
        }
        result.setParsedParameters(parsedParams);

        result.setValid(issues.isEmpty());
        result.setIssues(issues);
        result.setPositiveFindings(positiveFindings);

        // Generar forma canónica
        StringBuilder canonical = new StringBuilder("GET [base]/").append(resourceTypePart);
        if (!parsedParams.isEmpty()) {
            canonical.append("?");
            List<String> paramParts = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : parsedParams.entrySet()) {
                for (String val : entry.getValue()) {
                    paramParts.add(entry.getKey() + "=" + val);
                }
            }
            canonical.append(String.join("&", paramParts));
        }
        result.setCanonicalForm(canonical.toString());

        return result;
    }

    private void analyzeSearchParameter(String key, String value, String resourceType, List<String> issues, List<String> positiveFindings) {
        if (key.startsWith("_include")) {
            if (!value.contains(":")) {
                issues.add("Sintaxis _include inválida: '" + value + "'. Debe tener formato '[RecursoOrigen]:[parámetroReferencia]', ej: 'Observation:subject'.");
            } else {
                positiveFindings.add("Parámetro _include válido para incluir recursos referenciados: " + value);
            }
        } else if (key.startsWith("_revinclude")) {
            if (!value.contains(":")) {
                issues.add("Sintaxis _revinclude inválida: '" + value + "'. Debe tener formato '[RecursoInverso]:[parámetroReferencia]', ej: 'Provenance:target'.");
            } else {
                positiveFindings.add("Parámetro _revinclude válido para inclusión inversa: " + value);
            }
        } else if (key.contains(":exact")) {
            positiveFindings.add("Modificador :exact aplicado correctamente para búsqueda literal sensible a mayúsculas y acentos.");
        } else if (key.contains(":contains")) {
            positiveFindings.add("Modificador :contains aplicado para búsqueda por subcadena insensible a mayúsculas.");
        } else if (key.contains(":missing")) {
            if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                issues.add("El modificador :missing debe tener valor 'true' o 'false', valor actual: '" + value + "'.");
            } else {
                positiveFindings.add("Modificador :missing=" + value + " para comprobar existencia o ausencia del elemento.");
            }
        }

        // Verificación de códigos con sistema (system|code)
        if ("code".equals(key) || key.startsWith("code:")) {
            if (value.contains("|")) {
                positiveFindings.add("Uso óptimo de código con espacio de nombres/sistema URI: '" + value + "'.");
            } else if (!value.isEmpty()) {
                positiveFindings.add("Búsqueda de código sin sistema especificado (buscará en cualquier vocabulario).");
            }
        }

        // Verificación de prefijos numéricos/fechas
        if (value.length() >= 2) {
            String prefix = value.substring(0, 2);
            Set<String> validPrefixes = Set.of("eq", "ne", "gt", "lt", "ge", "le", "sa", "eb", "ap");
            if (validPrefixes.contains(prefix.toLowerCase())) {
                positiveFindings.add("Prefijo de comparación FHIR '" + prefix + "' detectado.");
            }
        }
    }
}
