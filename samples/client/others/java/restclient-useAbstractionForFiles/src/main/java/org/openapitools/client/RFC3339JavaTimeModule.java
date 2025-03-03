/*
 * Response file abstraction test
 * No description provided (generated by Openapi Generator https://github.com/openapitools/openapi-generator)
 *
 * The version of the OpenAPI document: 1.0.0
 * 
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */

package org.openapitools.client;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

import com.fasterxml.jackson.databind.module.SimpleModule;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", comments = "Generator version: 7.12.0-SNAPSHOT")
public class RFC3339JavaTimeModule extends SimpleModule {

    public RFC3339JavaTimeModule() {
        super("RFC3339JavaTimeModule");

        addDeserializer(Instant.class, RFC3339InstantDeserializer.INSTANT);
        addDeserializer(OffsetDateTime.class, RFC3339InstantDeserializer.OFFSET_DATE_TIME);
        addDeserializer(ZonedDateTime.class, RFC3339InstantDeserializer.ZONED_DATE_TIME);
    }
}
