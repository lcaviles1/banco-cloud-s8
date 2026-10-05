package cl.duoc.bank.web;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/web")
public class WebDashboardController {
    private final WebDashboardService service;

    public WebDashboardController(WebDashboardService service) {
        this.service = service;
    }

    @GetMapping("/accounts/{accountId}/dashboard")
    ResponseEntity<WebDashboardService.WebDashboard> dashboard(@PathVariable long accountId) {
        return ResponseEntity.ok()
                .header("X-BFF-Channel", "web-rich")
                .cacheControl(CacheControl.maxAge(10, TimeUnit.SECONDS).cachePrivate())
                .body(service.dashboard(accountId));
    }
}
