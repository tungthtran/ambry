/*
 * Copyright 2024 LinkedIn Corp. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 */
package com.github.ambry.frontend.s3;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.github.ambry.commons.ByteBufferReadableStreamChannel;
import com.github.ambry.commons.Callback;
import com.github.ambry.frontend.NamedBlobListEntry;
import com.github.ambry.frontend.NamedBlobListHandler;
import com.github.ambry.frontend.Page;
import com.github.ambry.rest.RestRequest;
import com.github.ambry.rest.RestResponseChannel;
import com.github.ambry.rest.RestServiceException;
import com.github.ambry.rest.RestUtils;
import com.github.ambry.router.ReadableStreamChannel;
import com.github.ambry.utils.ByteBufferDataInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import org.json.JSONObject;
import org.json.JSONTokener;


/**
 * Handles S3 requests for listing blobs that start with a provided prefix.
 * API reference: <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_ListObjectsV2.html">...</a>
 */
public class S3ListHandler {
  private final NamedBlobListHandler namedBlobListHandler;
  private RestRequest restRequest;
  public static final String PREFIX_PARAM_NAME = "prefix";
  public static final String MAXKEYS_PARAM_NAME = "max-keys";
  public static final String DELIMITER_PARAM_NAME = "delimiter";
  public static final String ENCODING_TYPE_PARAM_NAME = "encoding-type";
  private static final ObjectMapper xmlMapper = new XmlMapper();

  /**
   * Constructs a handler for handling s3 requests for listing blobs.
   * @param namedBlobListHandler named blob list handler
   */
  public S3ListHandler(NamedBlobListHandler namedBlobListHandler) {
    this.namedBlobListHandler = namedBlobListHandler;
  }

  /**
   * Asynchronously get account metadata.
   * @param restRequest the {@link RestRequest} that contains the request parameters and body.
   * @param restResponseChannel the {@link RestResponseChannel} where headers should be set.
   * @param callback the {@link Callback} to invoke when the response is ready (or if there is an exception).
   */
  public void handle(RestRequest restRequest, RestResponseChannel restResponseChannel,
      Callback<ReadableStreamChannel> callback) {
    this.restRequest = restRequest;
    namedBlobListHandler.handle(restRequest, restResponseChannel, ((result, exception) -> {
      if (exception != null) {
        callback.onCompletion(result, exception);
        return;
      }

      try {
        // Convert from json response to S3 xml response as defined in
        // https://docs.aws.amazon.com/AmazonS3/latest/API/API_ListObjectsV2.html#API_ListObjectsV2_ResponseSyntax.
        ByteBuffer byteBuffer = ((ByteBufferReadableStreamChannel) result).getContent();
        JSONObject jsonObject = new JSONObject(new JSONTokener(new ByteBufferDataInputStream(byteBuffer)));
        Page<NamedBlobListEntry> page = Page.fromJson(jsonObject, NamedBlobListEntry::new);
        ReadableStreamChannel readableStreamChannel = serializeAsXml(page);
        restResponseChannel.setHeader(RestUtils.Headers.DATE, new GregorianCalendar().getTime());
        restResponseChannel.setHeader(RestUtils.Headers.CONTENT_TYPE, "application/xml");
        restResponseChannel.setHeader(RestUtils.Headers.CONTENT_LENGTH, readableStreamChannel.getSize());
        callback.onCompletion(readableStreamChannel, null);
      } catch (Exception e) {
        callback.onCompletion(null, e);
      }
    }));
  }

  private ReadableStreamChannel serializeAsXml(Page<NamedBlobListEntry> namedBlobRecordPage)
      throws IOException, RestServiceException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ListBucketResult listBucketResult = new ListBucketResult();
    String maxKeys = RestUtils.getHeader(restRequest.getArgs(), MAXKEYS_PARAM_NAME, false);
    int maxKeysValue = maxKeys == null ? Integer.MAX_VALUE : Integer.parseInt(maxKeys);

    // Iterate through list of blob names.
    List<Contents> contentsList = new ArrayList<>();
    int keyCount = 0;
    for (NamedBlobListEntry namedBlobRecord : namedBlobRecordPage.getEntries()) {
      Contents contents = new Contents();
      String blobName = namedBlobRecord.getBlobName();
      contents.setKey(blobName);
      String todayDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(Calendar.getInstance().getTime());
      contents.setLastModified(todayDate);
      contentsList.add(contents);
      if (++keyCount == maxKeysValue) {
        break;
      }
    }

    // Construct ListBucketResult pojo
    listBucketResult.setName(restRequest.getPath());
    String prefix = RestUtils.getHeader(restRequest.getArgs(), PREFIX_PARAM_NAME, false);
    if (prefix != null) {
      listBucketResult.setPrefix(prefix);
    }
    String delimiter = RestUtils.getHeader(restRequest.getArgs(), DELIMITER_PARAM_NAME, false);
    if (delimiter != null) {
      listBucketResult.setDelimiter(delimiter);
    }
    String encodingType = RestUtils.getHeader(restRequest.getArgs(), ENCODING_TYPE_PARAM_NAME, false);
    if (encodingType != null) {
      listBucketResult.setEncodingType(encodingType);
    }
    if (maxKeys != null) {
      listBucketResult.setMaxKeys(maxKeysValue);
    }
    listBucketResult.setKeyCount(keyCount);
    listBucketResult.setContents(contentsList);

    // Serialize xml
    xmlMapper.writeValue(outputStream, listBucketResult);
    return new ByteBufferReadableStreamChannel(ByteBuffer.wrap(outputStream.toByteArray()));
  }
}
