package com.revenda.payments.infrastructure.adapters.in.web;

import com.revenda.payments.domain.ports.in.CancelChargeUseCase;
import com.revenda.payments.domain.ports.in.ChargeCreation;
import com.revenda.payments.domain.ports.in.CreateChargeUseCase;
import com.revenda.payments.domain.ports.in.FindChargeUseCase;
import com.revenda.payments.domain.ports.in.RefundChargeUseCase;
import com.revenda.payments.domain.ports.in.SimulatePaymentCommand;
import com.revenda.payments.domain.ports.in.SimulatePaymentUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/charges")
@RequiredArgsConstructor
class ChargeController {

    private final CreateChargeUseCase createCharge;
    private final FindChargeUseCase findCharge;
    private final CancelChargeUseCase cancelCharge;
    private final RefundChargeUseCase refundCharge;
    private final SimulatePaymentUseCase simulatePayment;

    @PostMapping
    ResponseEntity<CreatedChargeResponse> create(@Valid @RequestBody ChargeRequest request) {
        ChargeCreation creation = createCharge.create(request.toCommand());
        return ResponseEntity.status(creation.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(CreatedChargeResponse.of(creation.charge()));
    }

    @GetMapping
    ChargeResponse find(@RequestParam(required = false) @NotBlank(message = "Informe a referência da cobrança.")
            String referenceId) {
        return ChargeResponse.of(findCharge.findByReference(referenceId.strip()));
    }

    @PostMapping("/{id}/cancel")
    ChargeResponse cancel(@PathVariable String id) {
        return ChargeResponse.of(cancelCharge.cancel(id));
    }

    @PostMapping("/{id}/refund")
    ChargeResponse refund(@PathVariable String id) {
        return ChargeResponse.of(refundCharge.refund(id));
    }

    @PostMapping("/{id}/simulate")
    ResponseEntity<SimulationResponse> simulate(@PathVariable String id, @Valid @RequestBody SimulationRequest request) {
        SimulatePaymentCommand command = request.toCommand(id);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(SimulationResponse.of(simulatePayment.simulate(command), command.deliveries()));
    }
}
