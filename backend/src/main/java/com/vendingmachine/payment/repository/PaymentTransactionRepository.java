package com.vendingmachine.payment.repository;

import com.vendingmachine.payment.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    boolean existsByProviderAndProviderTransactionId(String provider, String providerTransactionId);

    Optional<PaymentTransaction> findByProviderAndProviderTransactionId(String provider, String providerTransactionId);

    List<PaymentTransaction> findByOrderId(UUID orderId);

    List<PaymentTransaction> findByTransferReference(String transferReference);
}
