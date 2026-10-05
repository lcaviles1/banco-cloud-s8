package cl.duoc.bank.mobile;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/mobile")
public class MobileHomeController {
    private final MobileHomeService service;

    public MobileHomeController(MobileHomeService service) {
        this.service = service;
    }

    @GetMapping("/accounts/{accountId}/home")
    ResponseEntity<MobileHomeService.MobileHome> home(@PathVariable long accountId) {
        return ResponseEntity.ok()
                .header("X-BFF-Channel", "mobile-compact")
                .cacheControl(CacheControl.maxAge(15, TimeUnit.SECONDS).cachePrivate())
                .body(service.home(accountId));
    }
}
