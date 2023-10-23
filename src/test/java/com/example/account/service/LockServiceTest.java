package com.example.account.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.example.account.exception.AccountException;
import com.example.account.type.ErrorCode;
import io.reactivex.rxjava3.internal.subscribers.BlockingSubscriber;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

@ExtendWith(MockitoExtension.class)
class LockServiceTest {
    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    @InjectMocks
    private LockService lockService;

    @Test
    void successGetLock() throws InterruptedException {
        // Given
        given(redissonClient.getLock(anyString()))
            .willReturn(rLock);
        given(rLock.tryLock(anyLong(), anyLong(), any()))
            .willReturn(true);

        // When
        // Then
        assertDoesNotThrow(() -> lockService.lock("123"));
    }

    @Test
    void failGetLock() throws InterruptedException{
        // Given
        given(redissonClient.getLock(anyString()))
            .willReturn(rLock);
        given(rLock.tryLock(anyLong(), anyLong(), any()))
            .willReturn(false);

        // When
        AccountException exception = assertThrows(AccountException.class,
            () -> lockService.lock("123"));

        // Then
        assertEquals(ErrorCode.ACCOUNT_TRANSACTION_LOCK, exception.getErrorCode());
    }

}