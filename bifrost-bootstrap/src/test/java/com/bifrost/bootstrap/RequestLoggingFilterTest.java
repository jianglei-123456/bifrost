package com.bifrost.bootstrap;

import com.bifrost.bootstrap.web.RequestLoggingFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 请求日志过滤器单测：原样 URL 落日志、凭据（p/t/s）打码。
 *
 * <p>排查"客户端某页面没数据"时靠的就是这行日志，所以"URL 完整"与"凭据不落盘"
 * 两条都不能回归。</p>
 */
class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void logsFullUrlAndMasksQueryCredentials(CapturedOutput output) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/rest/getAlbumList2.view");
        request.setQueryString("u=admin&t=deadbeef&s=salty&v=1.16.1&c=DSub&f=json&type=random&size=10");
        request.setRemoteAddr("192.168.1.20");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        String log = output.getOut();
        assertTrue(log.contains("/rest/getAlbumList2.view?u=admin&t=***&s=***&v=1.16.1&c=DSub"
                + "&f=json&type=random&size=10"), log);
        assertTrue(log.contains("192.168.1.20"), log);
        assertTrue(log.contains("<-- 200 GET /rest/getAlbumList2.view"), log);
        assertFalse(log.contains("deadbeef"), log);
        assertFalse(log.contains("salty"), log);
    }

    /**
     * 表单 POST：参数在 body 里（客户端用 POST 调 createPlaylist/scrobble 时就是这样）。
     * MockHttpServletRequest 不模拟容器解析 body 参数，所以这里按容器的结果显式 setParameter
     * ——过滤器读到的正是容器解析并缓存后的参数字典（Tomcat 只解析一次，控制器随后读同一份）。
     */
    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void logsFormPostParametersAndMasksPassword(CapturedOutput output) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/rest/scrobble");
        request.setContentType("application/x-www-form-urlencoded");
        request.setRemoteAddr("192.168.1.21");
        request.setParameter("u", "admin");
        request.setParameter("p", "secret");
        request.setParameter("v", "1.16.1");
        request.setParameter("c", "DSub");
        request.setParameter("id", "tr-1");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        String log = output.getOut();
        assertTrue(log.contains("--> POST /rest/scrobble?"), log);
        assertTrue(log.contains("u=admin"), log);
        assertTrue(log.contains("p=***"), log);
        assertTrue(log.contains("c=DSub"), log);
        assertTrue(log.contains("id=tr-1"), log);
        assertFalse(log.contains("secret"), log);
    }
}
