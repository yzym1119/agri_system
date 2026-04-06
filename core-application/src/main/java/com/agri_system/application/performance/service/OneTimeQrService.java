package com.agri_system.application.performance.service;

import com.agri_system.application.performance.config.PerformanceProperties;
import com.agri_system.application.performance.dto.QrBindCommand;
import com.agri_system.application.performance.dto.QrCreateResultVO;
import com.agri_system.application.performance.dto.QrStatusVO;
import com.agri_system.application.performance.event.CustomerBindApprovedEvent;
import com.agri_system.domain.performance.model.OneTimeQr;
import com.agri_system.domain.performance.repository.OneTimeQrRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OneTimeQrService {

    private final OneTimeQrRepository oneTimeQrRepository;
    private final PerformanceProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(rollbackFor = Exception.class)
    public QrCreateResultVO create(String orderTempToken, Long staffId, Long storeId) {
        OneTimeQr qr = new OneTimeQr();
        qr.setQrToken(UUID.randomUUID().toString().replace("-", ""));
        qr.setOrderTempToken(orderTempToken);
        qr.setStaffId(staffId);
        qr.setStoreId(storeId);
        qr.setStatus(OneTimeQr.STATUS_CREATED);
        qr.setExpireAt(LocalDateTime.now().plusSeconds(properties.getQrExpireSeconds()));
        qr.setSceneData("order:" + orderTempToken + ";staff:" + staffId);
        oneTimeQrRepository.save(qr);

        QrCreateResultVO result = new QrCreateResultVO();
        result.setQrToken(qr.getQrToken());
        result.setExpireAt(qr.getExpireAt());
        String mockImage = java.util.Base64.getEncoder()
                .encodeToString(("QR:" + qr.getQrToken()).getBytes(StandardCharsets.UTF_8));
        result.setImageBase64(mockImage);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void bind(QrBindCommand command) {
        OneTimeQr qr = oneTimeQrRepository.findByToken(command.getQrToken())
                .orElseThrow(() -> new IllegalArgumentException("二维码不存在或已失效"));
        if (qr.getStatus() != OneTimeQr.STATUS_CREATED) {
            throw new IllegalStateException("二维码已被使用");
        }
        if (qr.isExpired()) {
            qr.setStatus(OneTimeQr.STATUS_EXPIRED);
            oneTimeQrRepository.save(qr);
            throw new IllegalStateException("二维码已过期");
        }
        qr.setStatus(OneTimeQr.STATUS_BOUND);
        qr.setCustomerId(command.getCustomerId());
        qr.setOpenid(command.getOpenid());
        qr.setBindAt(LocalDateTime.now());
        oneTimeQrRepository.save(qr);

        eventPublisher.publishEvent(new CustomerBindApprovedEvent(
                command.getCustomerId(),
                qr.getStaffId(),
                "QR",
                null));
    }

    public QrStatusVO getStatus(String token) {
        OneTimeQr qr = oneTimeQrRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("二维码不存在"));
        QrStatusVO vo = new QrStatusVO();
        vo.setQrToken(token);
        vo.setStatus(qr.getStatus());
        vo.setCustomerId(qr.getCustomerId());
        vo.setBindAt(qr.getBindAt());
        return vo;
    }
}

