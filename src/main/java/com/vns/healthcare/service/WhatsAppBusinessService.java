package com.vns.healthcare.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class WhatsAppBusinessService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppBusinessService.class);

    private final String apiUrl;
    private final String apiVersion;
    private final String token;
    private final String phoneNumberId;
    private final boolean enabled;
    private final RestTemplate restTemplate;

    public WhatsAppBusinessService(@Value("${app.whatsapp.enabled:false}") boolean enabled,
                                   @Value("${app.whatsapp.api-url:https://graph.facebook.com}") String apiUrl,
                                   @Value("${app.whatsapp.api-version:v18.0}") String apiVersion,
                                   @Value("${app.whatsapp.token:}") String token,
                                   @Value("${app.whatsapp.phone-number-id:}") String phoneNumberId) {
        this.enabled = enabled;
        this.apiUrl = apiUrl;
        this.apiVersion = apiVersion;
        this.token = token;
        this.phoneNumberId = phoneNumberId;
        this.restTemplate = new RestTemplate();
    }

    public boolean isConfigured() {
        return enabled && StringUtils.hasText(token) && StringUtils.hasText(phoneNumberId);
    }

    public void sendPdf(String phoneNumber, byte[] pdfBytes, String fileName, String caption) {
        if (!isConfigured()) {
            throw new IllegalStateException("WhatsApp Business API is not configured. Set app.whatsapp.token and app.whatsapp.phone-number-id.");
        }

        Assert.hasText(phoneNumber, "Phone number is required");
        Assert.notNull(pdfBytes, "PDF bytes are required");
        String safeFileName = StringUtils.hasText(fileName) ? fileName : "brochure.pdf";
        String to = normalizePhoneNumber(phoneNumber);
        String mediaId = uploadMedia(pdfBytes, safeFileName);
        sendDocumentMessage(to, mediaId, safeFileName, caption);
    }

    private String uploadMedia(byte[] pdfBytes, String fileName) {
        String url = apiUrl + "/" + apiVersion + "/" + phoneNumberId + "/media";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<String, Object>();
        body.add("messaging_product", "whatsapp");
        body.add("type", "document");
        body.add("file", createFileResource(pdfBytes, fileName));

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<MultiValueMap<String, Object>>(body, headers);
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);
        Map<String, Object> payload = response.getBody();

        if (!response.getStatusCode().is2xxSuccessful() || payload == null || payload.get("id") == null) {
            String errorText = payload == null ? "No response body" : payload.toString();
            throw new IllegalStateException("WhatsApp media upload failed: " + errorText);
        }

        return String.valueOf(payload.get("id"));
    }

    private void sendDocumentMessage(String to, String mediaId, String fileName, String caption) {
        String url = apiUrl + "/" + apiVersion + "/" + phoneNumberId + "/messages";

        Map<String, Object> document = new HashMap<String, Object>();
        document.put("id", mediaId);
        document.put("filename", fileName);
        if (StringUtils.hasText(caption)) {
            document.put("caption", caption);
        }

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", to);
        payload.put("type", "document");
        payload.put("document", document);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        HttpEntity<Map<String, Object>> request = new HttpEntity<Map<String, Object>>(payload, headers);
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);
        Map<String, Object> body = response.getBody();

        if (!response.getStatusCode().is2xxSuccessful() || body == null || body.get("messages") == null) {
            String errorText = body == null ? "No response body" : body.toString();
            throw new IllegalStateException("WhatsApp document send failed: " + errorText);
        }

        log.info("WhatsApp PDF sent successfully to {} with mediaId {}", to, mediaId);
    }

    private ByteArrayResource createFileResource(byte[] bytes, String fileName) {
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
    }

    private String normalizePhoneNumber(String phoneNumber) {
        String cleaned = phoneNumber == null ? "" : phoneNumber.trim();
        cleaned = cleaned.replace(" ", "").replace("-", "").replace("(", "").replace(")", "");
        if (!cleaned.startsWith("+")) {
            cleaned = "+" + cleaned;
        }
        return cleaned;
    }
}
