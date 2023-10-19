package com.example.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.account.domain.Account;
import com.example.account.domain.AccountUser;
import com.example.account.dto.AccountDto;
import com.example.account.exception.AccountException;
import com.example.account.repository.AccountRepository;
import com.example.account.repository.AccountUserRepository;
import com.example.account.type.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountUserRepository accountUserRepository;

    @InjectMocks
    private AccountService accountService;

    @Test
    void createAccountSuccess() {
        // Given
        AccountUser user = AccountUser.builder()
            .id(12L)
            .name("dooli")
            .build();

        given(accountUserRepository.findById(anyLong()))
            .willReturn(Optional.of(user));

        given(accountRepository.findFirstByOrderByIdDesc())
            .willReturn(Optional.of(Account.builder()
                .accountNumber("1000000012").build()));

        given(accountRepository.save(any()))
            .willReturn(Account.builder()
                .accountUser(user)
                .accountNumber("1000000015").build());

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);

        // When
        AccountDto accountDto = accountService.createAccount(1L, 1000L);

        // Then
        verify(accountRepository, times(1)).save(captor.capture());
        assertEquals(12L, accountDto.getUserId());
        assertEquals("1000000013", captor.getValue().getAccountNumber());

    }

    @Test
    void createFirstAccount() {
        // Given
        AccountUser user = AccountUser.builder()
            .id(15L)
            .name("dooli")
            .build();

        given(accountUserRepository.findById(anyLong()))
            .willReturn(Optional.of(user));

        given(accountRepository.findFirstByOrderByIdDesc())
            .willReturn(Optional.empty());

        given(accountRepository.save(any()))
            .willReturn(Account.builder()
                .accountUser(user)
                .accountNumber("1000000015").build());

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);

        // When
        AccountDto accountDto = accountService.createAccount(1L, 1000L);

        // Then
        verify(accountRepository, times(1)).save(captor.capture());
        assertEquals(15L, accountDto.getUserId());
        assertEquals("1000000000", captor.getValue().getAccountNumber());

    }

    @Test
    @DisplayName("해당 유저 없음 - 계좌 생성 실패")
    void createAccount_UserNotFound() {
        // Given
        given(accountUserRepository.findById(anyLong()))
            .willReturn(Optional.empty());

        // When
        AccountException exception = assertThrows(AccountException.class,
            () -> accountService.createAccount(1L, 1000L));

        // Then
        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
    }
}