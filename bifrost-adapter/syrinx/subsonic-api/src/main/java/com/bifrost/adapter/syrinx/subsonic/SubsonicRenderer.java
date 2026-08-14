package com.bifrost.adapter.syrinx.subsonic;

import com.bifrost.adapter.syrinx.subsonic.dto.SubsonicResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Subsonic 响应渲染：格式完全由 f 参数决定（f=json 切 JSON，默认 XML），
 * 不做 Accept 内容协商（《Subsonic_API_参考》§3.2）。
 */
@Component
@RequiredArgsConstructor
public class SubsonicRenderer {

    private final ObjectMapper jsonMapper;
    private final XmlMapper xmlMapper = new XmlMapper();

    /** 常规端点渲染（XML 默认；f=json 输出 {"subsonic-response": {...}}）。 */
    public ResponseEntity<String> render(HttpServletRequest request, SubsonicResponse body) {
        String f = request.getParameter("f");
        if ("json".equalsIgnoreCase(f)) {
            try {
                String json = jsonMapper.writeValueAsString(Map.of("subsonic-response", body));
                return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("JSON 序列化失败", e);
            }
        }
        try {
            String xml = xmlMapper.writeValueAsString(body);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(xml);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("XML 序列化失败", e);
        }
    }

    /** 二进制端点错误渲染：始终 XML，Content-Type 以 text/xml 开头（协议要求）。 */
    public ResponseEntity<String> renderBinaryError(SubsonicResponse body) {
        try {
            String xml = xmlMapper.writeValueAsString(body);
            return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/xml;charset=UTF-8")).body(xml);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("XML 序列化失败", e);
        }
    }
}
