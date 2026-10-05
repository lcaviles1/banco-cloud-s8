package cl.duoc.bank.movement;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
public class MovementController {
    private final LegacyMovementRepository repository;

    public MovementController(LegacyMovementRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/accounts/{accountId}/movements-overview")
    LegacyMovementRepository.Overview overview(@PathVariable long accountId,
                                                @RequestParam(defaultValue = "10") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return repository.overview(accountId, safeLimit);
    }

    @GetMapping("/data-quality")
    LegacyMovementRepository.DataQuality quality() {
        return repository.quality();
    }
}
