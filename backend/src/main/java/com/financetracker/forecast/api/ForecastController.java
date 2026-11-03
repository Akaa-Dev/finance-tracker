package com.financetracker.forecast.api;

import com.financetracker.common.CurrentOwner;
import com.financetracker.forecast.application.ForecastService;
import com.financetracker.forecast.api.ForecastContracts.*;
import java.security.Principal;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/forecasts")
public class ForecastController {
    private final ForecastService forecasts;
    private final CurrentOwner current;

    public ForecastController(ForecastService forecasts, CurrentOwner current) {
        this.forecasts = forecasts;
        this.current = current;
    }

    @GetMapping("/cashflow")
    public Forecast cashflow(Principal principal, @RequestParam LocalDate from, @RequestParam LocalDate to) {
        return forecasts.cashflow(current.id(principal), from, to);
    }

    @PostMapping("/scenarios")
    public Scenario scenario(Principal principal, @RequestBody ScenarioRequest request) {
        return forecasts.scenario(current.id(principal), request);
    }
}
