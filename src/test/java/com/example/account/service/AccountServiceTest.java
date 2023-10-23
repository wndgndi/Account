package com.example.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.account.domain.Account;
import com.example.account.domain.AccountUser;
import com.example.account.dto.AccountDto;
import com.example.account.exception.AccountException;
import com.example.account.repository.AccountRepository;
import com.example.account.repository.AccountUserRepository;
import com.example.account.type.AccountStatus;
import com.example.account.type.ErrorCode;
import java.util.Arrays;
import java.util.List;
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

    @Test
    @DisplayName("유저 당 최대 계좌는 10개")
    void createAccount_maxAccountIs10() {
        // Given
        AccountUser user = AccountUser.builder()
            .id(15L)
            .name("dooli")
            .build();

        given(accountUserRepository.findById(anyLong()))
            .willReturn(Optional.of(user));
        given(accountRepository.countByAccountUser(any()))
            .willReturn(10);

        // When
        AccountException exception = assertThrows(AccountException.class,
            () -> accountService.createAccount(1L, 1000L));

        // Then
        assertEquals(ErrorCode.MAX_ACCOUNT_PER_USER_10, exception.getErrorCode());
    }

    @Test
    void deleteAccountSuccess() {
        // Given
        AccountUser user = AccountUser.builder()
            .id(12L)
            .name("dooli")
            .build();

        given(accountUserRepository.findById(anyLong()))
            .willReturn(Optional.of(user));

        given(accountRepository.findByAccountNumber(anyString()))
            .willReturn(Optional.of(Account.builder()
                .accountUser(user)
                .balance(0L)
                .accountNumber("1000000012").build()));

        given(accountRepository.save(any()))
            .willReturn(Account.builder()
                .accountUser(user)
                .accountNumber("1000000015").build());

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);

        // When
        AccountDto accountDto = accountService.deleteAccount(1L, "1234563212");

        // Then
        verify(accountRepository, times(1)).save(captor.capture());
        assertEquals(12L, accountDto.getUserId());
        assertEquals("1000000012", captor.getValue().getAccountNumber());
        assertEquals(AccountStatus.UNREGISTERED, captor.getValue().getAccountStatus());
    }

    @Test
    @DisplayName("해당 유저 없음 - 계좌 해지 실패")
    void deleteAccount_UserNotFound() {
        // Given
        given(accountUserRepository.findById(anyLong()))
            .willReturn(Optional.empty());

        // When
        AccountException exception = assertThrows(AccountException.class,
            () -> accountService.deleteAccount(1L, "1234567890"));

        // Then
        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("해당 계좌 없음 - 계좌 해지 실패")
    void deleteAccount_AccountNotFound() {
        // Given
        AccountUser user = AccountUser.builder()
            .id(12L)
            .name("dooli")
            .build();

        given(accountUserRepository.findById(anyLong()))
            .willReturn(Optional.of(user));

        given(accountRepository.findByAccountNumber(anyString()))
            .willReturn(Optional.empty());

        // When
        AccountException exception = assertThrows(AccountException.class,
            () -> accountService.deleteAccount(1L, "1234567890"));

        // Then
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("계좌 소유주 다름 - 계좌 해지 실패")
    void deleteAccountFailed_userUnMatch() {
        // Given
        AccountUser dooli = AccountUser.builder()
            .id(12L)
            .name("dooli")
            .build();

        AccountUser ddochi = AccountUser.builder()
            .id(13L)
            .name("ddochi")
            .build();

        given(accountUserRepository.findById(anyLong()))
            .willReturn(Optional.of(dooli));

        given(accountRepository.findByAccountNumber(anyString()))
            .willReturn(Optional.of(Account.builder()
                .accountUser(ddochi)
                .balance(0L)
                .accountNumber("1000000012")
                .build()));

        // when
        AccountException exception = assertThrows(AccountException.class,
            () -> accountService.deleteAccount(1L, "1234567890"));

        // then
        assertEquals(ErrorCode.USER_ACCOUNT_UN_MATCH, exception.getErrorCode());
    }

    @Test
    @DisplayName("해지 계좌는 잔액이 없어야 한다 - 계좌 해지 실패")
    void deleteAccountFailed_balanceNotEmpty() {
        // Given
        AccountUser dooli = AccountUser.builder()
            .id(12L)
            .name("dooli")
            .build();

        given(accountUserRepository.findById(anyLong()))
            .willReturn(Optional.of(dooli));

        given(accountRepository.findByAccountNumber(anyString()))
            .willReturn(Optional.of(Account.builder()
                .accountUser(dooli)
                .balance(10L)
                .accountNumber("1000000012")
                .build()));

        // when
        AccountException exception = assertThrows(AccountException.class,
            () -> accountService.deleteAccount(1L, "1234567890"));

        // then
        assertEquals(ErrorCode.BALANCE_NOT_EMPTY, exception.getErrorCode());
    }

    @Test
    @DisplayName("계좌가 이미 해지되었다 - 계좌 해지 실패")
    void deleteAccountFailed_alreadyUnregistered() {
        // Given
        AccountUser dooli = AccountUser.builder()
            .id(12L)
            .name("dooli")
            .build();

        given(accountUserRepository.findById(anyLong()))
            .willReturn(Optional.of(dooli));

        given(accountRepository.findByAccountNumber(anyString()))
            .willReturn(Optional.of(Account.builder()
                .accountUser(dooli)
                .balance(0L)
                .accountNumber("1000000012")
                .accountStatus(AccountStatus.UNREGISTERED)
                .build()));

        // when
        AccountException exception = assertThrows(AccountException.class,
            () -> accountService.deleteAccount(1L, "1234567890"));

        // then
        assertEquals(ErrorCode.ACCOUNT_ALREADY_UNREGISTERED, exception.getErrorCode());
    }

    @Test
    void successGetAccountsByUserId() {
        // Given
        AccountUser dooli = AccountUser.builder()
            .id(12L)
            .name("dooli")
            .build();

        List<Account> accounts = Arrays.asList(
            Account.builder()
                .accountUser(dooli)
                .accountNumber("1111111111")
                .balance(1000L)
                .build(),
            Account.builder()
                .accountUser(dooli)
                .accountNumber("2222222222")
                .balance(2000L)
                .build(),
            Account.builder()
                .accountUser(dooli)
                .accountNumber("3333333333")
                .balance(3000L)
                .build()
        );
        given(accountUserRepository.findById(anyLong()))
            .willReturn(Optional.of(dooli));
        given(accountRepository.findByAccountUser(any()))
            .willReturn(accounts);

        // When
        List<AccountDto> accountDtos = accountService.getAccountsByUserId(1L);

        // Then
        assertEquals(3, accountDtos.size());
        assertEquals("1111111111", accountDtos.get(0).getAccountNumber());
        assertEquals(1000, accountDtos.get(0).getBalance());
        assertEquals("2222222222", accountDtos.get(1).getAccountNumber());
        assertEquals(2000, accountDtos.get(1).getBalance());
        assertEquals("3333333333", accountDtos.get(2).getAccountNumber());
        assertEquals(3000, accountDtos.get(2).getBalance());
    }

    @Test
    void failedToGetAccounts() {
        // Given
        given(accountUserRepository.findById(anyLong()))
            .willReturn(Optional.empty());

        // When
        AccountException exception = assertThrows(AccountException.class,
            () -> accountService.getAccountsByUserId(1L));

        // Then
        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
    }

}