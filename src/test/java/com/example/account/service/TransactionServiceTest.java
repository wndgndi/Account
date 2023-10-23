package com.example.account.service;

import static com.example.account.type.AccountStatus.IN_USE;
import static com.example.account.type.TransactionResultType.F;
import static com.example.account.type.TransactionResultType.S;
import static com.example.account.type.TransactionType.CANCEL;
import static com.example.account.type.TransactionType.USE;
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
import com.example.account.domain.Transaction;
import com.example.account.dto.TransactionDto;
import com.example.account.exception.AccountException;
import com.example.account.repository.AccountRepository;
import com.example.account.repository.AccountUserRepository;
import com.example.account.repository.TransactionRepository;
import com.example.account.type.AccountStatus;
import com.example.account.type.ErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    public static final long USE_AMOUNT = 200L;
    public static final long CANCEL_AMOUNT = 200L;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountUserRepository accountUserRepository;

    @InjectMocks
    private TransactionService transactionService;

    @Test
    void successUseBalance() {
        // Given
        AccountUser user = AccountUser.builder()
            .id(12L)
            .name("Dooli")
            .build();

        Account account = Account.builder()
            .accountUser(user)
            .accountStatus(IN_USE)
            .balance(10000L)
            .accountNumber("1000000012")
            .build();

        given(accountUserRepository.findById(anyLong()))
            .willReturn(Optional.of(user));

        given(accountRepository.findByAccountNumber(anyString()))
            .willReturn(Optional.of(account));

        given(transactionRepository.save(any()))
            .willReturn(Transaction.builder()
                .account(account)
                .transactionType(USE)
                .transactionResultType(S)
                .transactionId("transactionId")
                .transactedAt(LocalDateTime.now())
                .amount(1000L)
                .balanceSnapshot(9000L)
                .build());

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);

        // When
        TransactionDto transactionDto = transactionService.useBalance(1L, "1000000000", 200L);

        // Then
        verify(transactionRepository, times(1)).save(captor.capture());
        assertEquals(200L, captor.getValue().getAmount());
        assertEquals(9800L, captor.getValue().getBalanceSnapshot());
        assertEquals(S, transactionDto.getTransactionResultType());
        assertEquals(USE, transactionDto.getTransactionType());
        assertEquals(9000L, transactionDto.getBalanceSnapshot());
        assertEquals(1000L, transactionDto.getAmount());
    }

    @Test
    @DisplayName("해당 유저 없음 - 잔액 사용 실패")
    void useBalanceFailed_UserNotFound() {
        // Given
        given(accountUserRepository.findById(anyLong()))
            .willReturn(Optional.empty());

        // When
        AccountException exception = assertThrows(AccountException.class,
            () -> transactionService.useBalance(1L, "1000000000", 1000L));

        // Then
        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("해당 계좌 없음 - 잔액 사용 실패")
    void useBalanceFailed_AccountNotFound() {
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
            () -> transactionService.useBalance(1L, "1234567890", 1000L));

        // Then
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("계좌 소유주 다름 - 잔액 사용 실패")
    void useBalanceFailed_userUnMatch() {
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
            () -> transactionService.useBalance(1L, "1234567890", 1000L));

        // then
        assertEquals(ErrorCode.USER_ACCOUNT_UN_MATCH, exception.getErrorCode());
    }

    @Test
    @DisplayName("계좌가 이미 해지되었다 - 잔액 사용 실패")
    void useBalanceFailed_alreadyUnregistered() {
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
            () -> transactionService.useBalance(1L, "1234567890", 1000L));

        // then
        assertEquals(ErrorCode.ACCOUNT_ALREADY_UNREGISTERED, exception.getErrorCode());
    }

    @Test
    @DisplayName("거래 금액이 잔액보다 큰 경우")
    void exceedAmount_useBalance() {
        AccountUser user = AccountUser.builder()
            .id(12L)
            .name("Dooli")
            .build();

        Account account = Account.builder()
            .accountUser(user)
            .accountStatus(IN_USE)
            .balance(100L)
            .accountNumber("1000000012")
            .build();

        // Given
        given(accountUserRepository.findById(anyLong()))
            .willReturn(Optional.of(user));

        given(accountRepository.findByAccountNumber(anyString()))
            .willReturn(Optional.of(account));

        // When
        // Then
        AccountException exception = assertThrows(AccountException.class,
            () -> transactionService.useBalance(1L, "1000000000", 1000L));
        verify(transactionRepository, times(0)).save(any());
        assertEquals(ErrorCode.AMOUNT_EXCEED_BALANCE, exception.getErrorCode());
    }

    @Test
    @DisplayName("실패 트랜잭션 저장 성공")
    void saveFailedUseTransaction() {
        // Given
        AccountUser user = AccountUser.builder()
            .id(12L)
            .name("Dooli")
            .build();

        Account account = Account.builder()
            .accountUser(user)
            .accountStatus(IN_USE)
            .balance(10000L)
            .accountNumber("1000000012")
            .build();

        given(accountRepository.findByAccountNumber(anyString()))
            .willReturn(Optional.of(account));

        given(transactionRepository.save(any()))
            .willReturn(Transaction.builder()
                .account(account)
                .transactionType(USE)
                .transactionResultType(S)
                .transactionId("transactionId")
                .transactedAt(LocalDateTime.now())
                .amount(1000L)
                .balanceSnapshot(9000L)
                .build());

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);

        // When
        transactionService.saveFailedUseTransaction("1000000000", USE_AMOUNT);

        // Then
        verify(transactionRepository, times(1)).save(captor.capture());
        assertEquals(200L, captor.getValue().getAmount());
        assertEquals(10000L, captor.getValue().getBalanceSnapshot());
        assertEquals(F, captor.getValue().getTransactionResultType());
    }

    @Test
    void successCancelTransaction() {
        // Given
        AccountUser user = AccountUser.builder()
            .id(12L)
            .name("Dooli")
            .build();

        Account account = Account.builder()
            .accountUser(user)
            .accountStatus(IN_USE)
            .balance(10000L)
            .accountNumber("1000000012")
            .build();

        Transaction transaction = Transaction.builder()
            .account(account)
            .transactionType(USE)
            .transactionResultType(S)
            .transactionId("transactionId")
            .transactedAt(LocalDateTime.now())
            .amount(CANCEL_AMOUNT)
            .balanceSnapshot(9000L)
            .build();

        given(transactionRepository.findByTransactionId(anyString()))
            .willReturn(Optional.of(transaction));

        given(accountRepository.findByAccountNumber(anyString()))
            .willReturn(Optional.of(account));

        given(transactionRepository.save(any()))
            .willReturn(Transaction.builder()
                .account(account)
                .transactionType(CANCEL)
                .transactionResultType(S)
                .transactionId("transactionForCancel")
                .transactedAt(LocalDateTime.now())
                .amount(CANCEL_AMOUNT)
                .balanceSnapshot(10000L)
                .build());

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);

        // When
        TransactionDto transactionDto = transactionService.cancelBalance("transactionId",
            "1000000000", CANCEL_AMOUNT);

        // Then
        verify(transactionRepository, times(1)).save(captor.capture());
        assertEquals(CANCEL_AMOUNT, captor.getValue().getAmount());
        assertEquals(10000L + CANCEL_AMOUNT, captor.getValue().getBalanceSnapshot());
        assertEquals(S, transactionDto.getTransactionResultType());
        assertEquals(CANCEL, transactionDto.getTransactionType());
        assertEquals(10000L, transactionDto.getBalanceSnapshot());
        assertEquals(CANCEL_AMOUNT, transactionDto.getAmount());
    }

    @Test
    @DisplayName("해당 계좌 없음 - 잔액 사용 취소 실패")
    void cancelTransactionFailed_AccountNotFound() {
        // Given
        given(transactionRepository.findByTransactionId(anyString()))
            .willReturn(Optional.of(Transaction.builder().build()));

        given(accountRepository.findByAccountNumber(anyString()))
            .willReturn(Optional.empty());

        // When
        AccountException exception = assertThrows(AccountException.class,
            () -> transactionService.cancelBalance("transactionId", "1234567890", 1000L));

        // Then
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("원 사용 거래 없음 - 잔액 사용 취소 실패")
    void cancelTransactionFailed_TransactionNotFound() {
        // Given
        given(transactionRepository.findByTransactionId(anyString()))
            .willReturn(Optional.empty());

        // When
        AccountException exception = assertThrows(AccountException.class,
            () -> transactionService.cancelBalance("transactionId", "1234567890", 1000L));

        // Then
        assertEquals(ErrorCode.TRANSACTION_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("거래와 계좌가 매칭 실패 - 잔액 사용 취소 실패")
    void cancelTransactionFailed_userUnMatch() {
        // Given
        AccountUser user = AccountUser.builder()
            .id(12L)
            .name("Dooli")
            .build();

        Account account = Account.builder()
            .id(1L)
            .accountUser(user)
            .accountStatus(IN_USE)
            .balance(10000L)
            .accountNumber("1000000012")
            .build();

        Account accountNotUse = Account.builder()
            .id(2L)
            .accountUser(user)
            .accountStatus(IN_USE)
            .balance(10000L)
            .accountNumber("1000000013")
            .build();

        Transaction transaction = Transaction.builder()
            .account(account)
            .transactionType(USE)
            .transactionResultType(S)
            .transactionId("transactionId")
            .transactedAt(LocalDateTime.now())
            .amount(CANCEL_AMOUNT)
            .balanceSnapshot(9000L)
            .build();

        given(transactionRepository.findByTransactionId(anyString()))
            .willReturn(Optional.of(transaction));

        given(accountRepository.findByAccountNumber(anyString()))
            .willReturn(Optional.of(accountNotUse));

        // when
        AccountException exception = assertThrows(AccountException.class,
            () -> transactionService.cancelBalance("transactionId", "1234567890", CANCEL_AMOUNT));

        // then
        assertEquals(ErrorCode.TRANSACTION_ACCOUNT_UN_MATCH, exception.getErrorCode());
    }

    @Test
    @DisplayName("거래금액과 취소금액이 다름 - 잔액 사용 취소 실패")
    void cancelTransactionFailed_CacelMustFully() {
        // Given
        AccountUser user = AccountUser.builder()
            .id(12L)
            .name("Dooli")
            .build();

        Account account = Account.builder()
            .id(1L)
            .accountUser(user)
            .accountStatus(IN_USE)
            .balance(10000L)
            .accountNumber("1000000012")
            .build();

        Transaction transaction = Transaction.builder()
            .account(account)
            .transactionType(USE)
            .transactionResultType(S)
            .transactionId("transactionId")
            .transactedAt(LocalDateTime.now())
            .amount(CANCEL_AMOUNT + 1000L)
            .balanceSnapshot(9000L)
            .build();

        given(transactionRepository.findByTransactionId(anyString()))
            .willReturn(Optional.of(transaction));

        given(accountRepository.findByAccountNumber(anyString()))
            .willReturn(Optional.of(account));

        // when
        AccountException exception = assertThrows(AccountException.class,
            () -> transactionService.cancelBalance("transactionId", "1234567890", CANCEL_AMOUNT));

        // then
        assertEquals(ErrorCode.CANCEL_MUST_FULLY, exception.getErrorCode());
    }

    @Test
    @DisplayName("취소는 1년까지만 가능 - 잔액 사용 취소 실패")
    void cancelTransactionFailed_TooOldOrder() {
        // Given
        AccountUser user = AccountUser.builder()
            .id(12L)
            .name("Dooli")
            .build();

        Account account = Account.builder()
            .id(1L)
            .accountUser(user)
            .accountStatus(IN_USE)
            .balance(10000L)
            .accountNumber("1000000012")
            .build();

        Transaction transaction = Transaction.builder()
            .account(account)
            .transactionType(USE)
            .transactionResultType(S)
            .transactionId("transactionId")
            .transactedAt(LocalDateTime.now().minusYears(1).minusDays(1))
            .amount(CANCEL_AMOUNT)
            .balanceSnapshot(9000L)
            .build();

        given(transactionRepository.findByTransactionId(anyString()))
            .willReturn(Optional.of(transaction));

        given(accountRepository.findByAccountNumber(anyString()))
            .willReturn(Optional.of(account));

        // when
        AccountException exception = assertThrows(AccountException.class,
            () -> transactionService.cancelBalance("transactionId", "1234567890", CANCEL_AMOUNT));

        // then
        assertEquals(ErrorCode.TOO_OLD_ORDER_TO_CANCEL, exception.getErrorCode());
    }
}