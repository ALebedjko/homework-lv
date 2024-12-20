package io.fourfinanceit.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import io.fourfinanceit.domain.Loan;
import io.fourfinanceit.domain.LoanExtension;
import io.fourfinanceit.domain.LoanRequest;
import io.fourfinanceit.exception.ExceptionJSONInfo;
import io.fourfinanceit.repository.LoanRepository;
import io.fourfinanceit.repository.LoanRequestRepository;
import io.fourfinanceit.service.LoanService;
import io.fourfinanceit.utils.DateUtils;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static io.fourfinanceit.exception.ExceptionMessages.*;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.springframework.http.HttpStatus.*;
import static org.springframework.http.MediaType.*;
import static org.springframework.test.util.AssertionErrors.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class LoanControllerTest extends AbstractControllerTest {

    private static final String LOANS_URL = "/loans/";

    private static final TypeReference<List<Loan>> LOAN_LIST_TYPE = new TypeReference<>() {
    };

    private static final TypeReference<ExceptionJSONInfo> EXCEPTION_JSON_TYPE = new TypeReference<>() {
    };

    private final Logger log = LoggerFactory.getLogger(LoanControllerTest.class);

    @Value("${MAX_LOAN_AMOUNT}")
    BigDecimal MAX_LOAN_AMOUNT;

    @Autowired
    LoanRequestRepository loanRequestRepository;

    @Autowired
    LoanService loanService;

    @Autowired
    LoanRepository loanRepository;

    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
    }

    @AfterEach
    public void tearDown() {
        DateUtils.reset();
        loanRequestRepository.deleteAll();
    }

    @Test
    public void loanShouldBeSuccessfullyExtended() throws Exception {
        String uri = LOANS_URL + "extendLoan";
        Optional<Loan> optionalExpectedExtendedLoan = loanRepository.findById(1L);
        Loan expectedExtendedLoan = optionalExpectedExtendedLoan.get();

        LoanExtension loanExtension = new LoanExtension();
        loanExtension.setId(1L);
        loanExtension.setExtensionTermInDays(14);
        loanExtension.setAdditionalInterest(new BigDecimal("30.30"));
        expectedExtendedLoan.setInterest(new BigDecimal("45.80"));
        expectedExtendedLoan.addLoanExtension(loanExtension);

        MvcResult result = mvc
                .perform(MockMvcRequestBuilders.post(uri).param("loanId", "1").param("extensionTermInDays", "14"))
                .andReturn();

        int mvcResultResponseStatus = result.getResponse().getStatus();
        HttpStatus httpResponseStatus = valueOf(mvcResultResponseStatus);

        Optional<Loan> optionalActualExtendedLoan = loanRepository.findById(1L);
        Loan actualExtendedLoan = optionalActualExtendedLoan.get();

        assertEquals("failure - expected status " + OK, OK, httpResponseStatus);
        assertEquals("expect should correspond actual loan", expectedExtendedLoan, actualExtendedLoan);
        assertEquals("extension count should match", expectedExtendedLoan.getLoanExtensions().get(0), actualExtendedLoan.getLoanExtensions().get(0));
    }

    @Test
    public void loanShouldBeSuccessfullyCreated() throws Exception {

        Loan expectedLoan = new Loan(new BigDecimal("50.00"), new BigDecimal("150.00"), 14);
        expectedLoan.setId(7L);
        expectedLoan.setLoanExtensions(new ArrayList<>());

        MvcResult result = mvc.perform(MockMvcRequestBuilders.post(LOANS_URL)
                        .contentType(APPLICATION_JSON)
                        .content(objectToJson(new LoanRequest(new BigDecimal(50), 14, "aaa-xxx0", "John", "Smith"))))
                .andReturn();
        String content = result.getResponse().getContentAsString();
        log.debug("content = " + content);

        int mvcResultResponseStatus = result.getResponse().getStatus();
        HttpStatus httpResponseStatus = valueOf(mvcResultResponseStatus);

        Loan actualLoan = loanService.listLoansByCustomerPersonalId("aaa-xxx0").get(0);

        assertEquals("loans should match", expectedLoan, actualLoan);
        assertEquals("failure - expected content " + OK, OK, httpResponseStatus);
    }


    @Test
    public void loanIsNotCreatedWhenOutsideWorkingHoursWithMaxAmount() throws Exception {
        String expectedExceptionMessage = DECLINED_DUE_RISK_ANALYSIS.getDescription();

        DateUtils.setNow(LocalDate.now().atStartOfDay());

        MvcResult result = mvc.perform(MockMvcRequestBuilders.post(LOANS_URL)
                        .contentType(APPLICATION_JSON)
                        .content(objectToJson(new LoanRequest(MAX_LOAN_AMOUNT, 1, "abc-xyz0", "John", "Smith"))))
                .andReturn();

        String content = result.getResponse().getContentAsString();
        ExceptionJSONInfo exceptionJSONInfo = super.mapFromJson(content, EXCEPTION_JSON_TYPE);
        String exceptionMessage = exceptionJSONInfo.getMessages().iterator().next();
        log.debug("exceptionMessage = " + exceptionMessage);

        HttpStatus httpResponseStatus = valueOf(result.getResponse().getStatus());
        assertEquals("failure - expected status " + INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR, httpResponseStatus);
        assertEquals("failure - expected exception message" + expectedExceptionMessage, expectedExceptionMessage, exceptionMessage);
    }

    @Test
    public void loanIsNotCreatedWhenMaxRequestAttemptsExceeded() throws Exception {
        String expectedExceptionMessage = DECLINED_DUE_RISK_ANALYSIS.getDescription();

        DateUtils.setNow(LocalDateTime.now());
        String uri = LOANS_URL;

        mvc.perform(MockMvcRequestBuilders.post(uri)
                        .contentType(APPLICATION_JSON)
                        .content(objectToJson(new LoanRequest(new BigDecimal(10), 1, "abc-xyz0", "John", "Smith"))))
                .andReturn();

        mvc.perform(MockMvcRequestBuilders.post(uri)
                        .contentType(APPLICATION_JSON)
                        .content(objectToJson(new LoanRequest(new BigDecimal(10), 1, "abc-xyz0", "John", "Smith"))))
                .andReturn();

        mvc.perform(MockMvcRequestBuilders.post(uri)
                        .contentType(APPLICATION_JSON)
                        .content(objectToJson(new LoanRequest(new BigDecimal(10), 1, "abc-xyz0", "John", "Smith"))))
                .andReturn();

        MvcResult result = mvc.perform(MockMvcRequestBuilders.post(uri)
                        .contentType(APPLICATION_JSON)
                        .content(objectToJson(new LoanRequest(new BigDecimal(10), 1, "abc-xyz0", "John", "Smith"))))
                .andReturn();

        String content = result.getResponse().getContentAsString();
        ExceptionJSONInfo exceptionJSONInfo = super.mapFromJson(content, EXCEPTION_JSON_TYPE);
        String exceptionMessage = exceptionJSONInfo.getMessages().iterator().next();

        HttpStatus httpResponseStatus = valueOf(result.getResponse().getStatus());
        assertEquals("failure - expected status " + INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR, httpResponseStatus);
        assertEquals("failure - expected exception message" + expectedExceptionMessage, expectedExceptionMessage, exceptionMessage);
    }

    @Test
    public void loanWithoutAmountIsNotCreated() throws Exception {
        String expectedExceptionMessage = AMOUNT_NOT_NULL_MSG.getDescription();

        MvcResult result = mvc.perform(MockMvcRequestBuilders
                        .post(LOANS_URL).contentType(APPLICATION_JSON)
                        .content(objectToJson(new LoanRequest(null, 1, "abc-xyz", "Vanja", "Ivanov"))))
                .andReturn();

        String content = result.getResponse().getContentAsString();
        ExceptionJSONInfo exceptionJSONInfo = super.mapFromJson(content, EXCEPTION_JSON_TYPE);
        String exceptionMessage = exceptionJSONInfo.getMessages().iterator().next();

        HttpStatus httpResponseStatus = valueOf(result.getResponse().getStatus());
        assertEquals("status should match", INTERNAL_SERVER_ERROR, httpResponseStatus);
        assertEquals("exception messages should match", expectedExceptionMessage, exceptionMessage);
    }

    @Test
    public void loanWithExceedingAmountIsNotCreated() throws Exception {
        String expectedExceptionMessage = "The attempt to take loan is made with amount, which is greater than max allowed amount. " +
                "Maximum loan amount is " + MAX_LOAN_AMOUNT;

        MvcResult result = mvc.perform(MockMvcRequestBuilders
                        .post(LOANS_URL).contentType(APPLICATION_JSON)
                        .content(objectToJson(
                                new LoanRequest(new BigDecimal(301), 1, "abc-xyz", "Vanja", "Ivanov"))))
                .andReturn();

        String content = result.getResponse().getContentAsString();
        ExceptionJSONInfo exceptionJSONInfo = super.mapFromJson(content, EXCEPTION_JSON_TYPE);
        String exceptionMessage = exceptionJSONInfo.getMessages().iterator().next();

        HttpStatus httpResponseStatus = valueOf(result.getResponse().getStatus());
        assertEquals("status should match", INTERNAL_SERVER_ERROR, httpResponseStatus);
        assertEquals("exception messages should match", expectedExceptionMessage, exceptionMessage);
    }

    @Test
    public void loanWithOutMandatoryFieldsIsNotCreated() throws Exception {
        List<String> expectedExceptionMessages = new ArrayList<>();
        expectedExceptionMessages.add(AMOUNT_NOT_NULL_MSG.getDescription());
        expectedExceptionMessages.add(TERM_NOT_NULL_MSG.getDescription());
        expectedExceptionMessages.add(NAME_NOT_NULL_MSG.getDescription());
        expectedExceptionMessages.add(SURNAME_NOT_NULL_MSG.getDescription());
        expectedExceptionMessages.add(PERSONAL_ID_NOT_NULL_MSG.getDescription());

        MvcResult result = mvc.perform(MockMvcRequestBuilders
                        .post(LOANS_URL)
                        .contentType(APPLICATION_JSON)
                        .content(objectToJson(new LoanRequest(null, null, "", "", ""))))
                .andReturn();

        String content = result.getResponse().getContentAsString();
        ExceptionJSONInfo exceptionJSONInfo = super.mapFromJson(content, EXCEPTION_JSON_TYPE);
        List<String> exceptionMessages = exceptionJSONInfo.getMessages();

        int status = result.getResponse().getStatus();
        assertEquals("status should be 500", INTERNAL_SERVER_ERROR.value(), status);
        assertEquals("failure - expected exception message" + expectedExceptionMessages, expectedExceptionMessages, exceptionMessages);
    }

    @Test
    public void listOfLoansShouldBeSuccessfullyRetrieved() throws Exception {
        String uri = "/loans/list";

        List<Loan> expectedLoans = loanRepository.findAll();

        MvcResult result = mvc.perform(MockMvcRequestBuilders.get(uri))
                .andReturn();

        String content = result.getResponse().getContentAsString();
        List<Loan> loanList = super.mapFromJson(content, LOAN_LIST_TYPE);

        int status = result.getResponse().getStatus();
        assertEquals("failure - expected status 200", OK.value(), status);
        assertThat(loanList).containsExactlyInAnyOrderElementsOf(expectedLoans);
    }

    @Test
    public void listOfLoanShouldBeRetrievedSuccessfullyByPersonalId() throws Exception {
        Loan firstExpectedLoan = new Loan(new BigDecimal("20.30"), new BigDecimal("20.50"), 10);
        firstExpectedLoan.setId(2L);

        Loan secondExpectedLoan = new Loan(new BigDecimal("100.50"), new BigDecimal("50.00"), 30);
        secondExpectedLoan.setId(3L);

        List<Loan> expectedLoans = new ArrayList<>();
        expectedLoans.add(firstExpectedLoan);
        expectedLoans.add(secondExpectedLoan);

        String personalId = "abc-xyz1";
        String uri = "/loans/personal-id/" + personalId;

        MvcResult result = mvc
                .perform(MockMvcRequestBuilders.get(uri))
                .andReturn();

        String content = result.getResponse().getContentAsString();
        List<Loan> loanList = super.mapFromJson(content, LOAN_LIST_TYPE);

        int status = result.getResponse().getStatus();
        assertEquals("status should be 200", OK.value(), status);
        assertEquals("loan should be equal", expectedLoans, loanList);
    }

}
