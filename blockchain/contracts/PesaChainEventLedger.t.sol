// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.28;

import {Test} from "forge-std/Test.sol";
import {PesaChainEventLedger} from "./PesaChainEventLedger.sol";

contract PesaChainEventLedgerTest is Test {
    PesaChainEventLedger private ledger;
    address private recorder = address(0xBEEF);
    bytes32 private eventId = keccak256("event-1");
    bytes32 private aggregateKey = keccak256("opaque-loan-key");

    function setUp() public {
        ledger = new PesaChainEventLedger(recorder);
    }

    function test_ConstructorAuthorizesOnlyInitialRecorder() public view {
        assertTrue(ledger.isRecorder(recorder));
        assertFalse(ledger.isRecorder(address(this)));
        assertEq(ledger.owner(), address(this));
    }

    function test_RecordsFinancialEventAndEmitsExactEvidence() public {
        vm.expectEmit(true, true, true, true, address(ledger));
        emit PesaChainEventLedger.LoanEventRecorded(
            eventId,
            aggregateKey,
            PesaChainEventLedger.LoanEventType.LOAN_DISBURSED,
            1234500,
            1_800_000_000,
            recorder
        );

        vm.prank(recorder);
        ledger.recordEvent(eventId, aggregateKey, PesaChainEventLedger.LoanEventType.LOAN_DISBURSED, 1234500, 1_800_000_000);

        assertTrue(ledger.eventExists(eventId));
        PesaChainEventLedger.LoanEvent memory stored = ledger.getEvent(eventId);
        assertEq(stored.aggregateKey, aggregateKey);
        assertEq(uint8(stored.eventType), uint8(PesaChainEventLedger.LoanEventType.LOAN_DISBURSED));
        assertEq(stored.amountMinorUnits, 1234500);
        assertEq(stored.occurredAt, 1_800_000_000);
        assertEq(stored.recorder, recorder);
    }

    function test_AllPermittedLifecycleEventTypesHaveCorrectAmountSemantics() public {
        PesaChainEventLedger.LoanEventType[5] memory kinds = [
            PesaChainEventLedger.LoanEventType.LOAN_APPROVED,
            PesaChainEventLedger.LoanEventType.LOAN_REJECTED,
            PesaChainEventLedger.LoanEventType.LOAN_DISBURSED,
            PesaChainEventLedger.LoanEventType.REPAYMENT_RECORDED,
            PesaChainEventLedger.LoanEventType.LOAN_COMPLETED
        ];
        for (uint256 i; i < kinds.length; ++i) {
            uint256 amount = (kinds[i] == PesaChainEventLedger.LoanEventType.LOAN_DISBURSED
                || kinds[i] == PesaChainEventLedger.LoanEventType.REPAYMENT_RECORDED) ? i + 1 : 0;
            bytes32 id = bytes32(i + 1);
            vm.prank(recorder);
            ledger.recordEvent(id, aggregateKey, kinds[i], amount, uint64(1_800_000_000 + i));
            assertTrue(ledger.eventExists(id));
            assertEq(uint8(ledger.getEvent(id).eventType), uint8(kinds[i]));
        }
    }

    function test_RevertsForUnauthorizedRecorder() public {
        vm.expectRevert(PesaChainEventLedger.Unauthorized.selector);
        ledger.recordEvent(eventId, aggregateKey, PesaChainEventLedger.LoanEventType.LOAN_APPROVED, 0, 1_800_000_000);
    }

    function test_RevertsOnDuplicateEventIdentifier() public {
        vm.prank(recorder);
        ledger.recordEvent(eventId, aggregateKey, PesaChainEventLedger.LoanEventType.LOAN_APPROVED, 0, 1_800_000_000);

        vm.expectRevert(PesaChainEventLedger.DuplicateEvent.selector);
        vm.prank(recorder);
        ledger.recordEvent(eventId, aggregateKey, PesaChainEventLedger.LoanEventType.REPAYMENT_RECORDED, 100, 1_800_000_001);
    }

    function test_RevertsForZeroIdentifiersAndTimestamp() public {
        vm.startPrank(recorder);
        vm.expectRevert(PesaChainEventLedger.InvalidInput.selector);
        ledger.recordEvent(bytes32(0), aggregateKey, PesaChainEventLedger.LoanEventType.LOAN_APPROVED, 0, 1_800_000_000);
        vm.expectRevert(PesaChainEventLedger.InvalidInput.selector);
        ledger.recordEvent(eventId, bytes32(0), PesaChainEventLedger.LoanEventType.LOAN_APPROVED, 0, 1_800_000_000);
        vm.expectRevert(PesaChainEventLedger.InvalidInput.selector);
        ledger.recordEvent(eventId, aggregateKey, PesaChainEventLedger.LoanEventType.LOAN_APPROVED, 0, 0);
        vm.stopPrank();
    }

    function test_AmountMustBePositiveOnlyForDisbursementAndRepayment() public {
        vm.startPrank(recorder);
        vm.expectRevert(PesaChainEventLedger.UnexpectedAmount.selector);
        ledger.recordEvent(eventId, aggregateKey, PesaChainEventLedger.LoanEventType.LOAN_APPROVED, 1, 1_800_000_000);
        vm.expectRevert(PesaChainEventLedger.UnexpectedAmount.selector);
        ledger.recordEvent(eventId, aggregateKey, PesaChainEventLedger.LoanEventType.REPAYMENT_RECORDED, 0, 1_800_000_000);
        vm.expectRevert(PesaChainEventLedger.UnexpectedAmount.selector);
        ledger.recordEvent(eventId, aggregateKey, PesaChainEventLedger.LoanEventType.LOAN_DISBURSED, 0, 1_800_000_000);
        vm.stopPrank();
    }

    function test_OnlyOwnerCanManageRecorderAndRevocationTakesEffect() public {
        address replacement = address(0xCAFE);
        ledger.setRecorder(replacement, true);
        assertTrue(ledger.isRecorder(replacement));

        vm.prank(replacement);
        ledger.recordEvent(eventId, aggregateKey, PesaChainEventLedger.LoanEventType.LOAN_APPROVED, 0, 1_800_000_000);
        ledger.setRecorder(replacement, false);

        vm.expectRevert(PesaChainEventLedger.Unauthorized.selector);
        vm.prank(replacement);
        ledger.recordEvent(bytes32(uint256(2)), aggregateKey, PesaChainEventLedger.LoanEventType.LOAN_APPROVED, 0, 1_800_000_001);
    }

    function testFuzz_EventEvidenceIsImmutable(bytes32 otherAggregateKey, uint64 timestamp) public {
        vm.assume(otherAggregateKey != bytes32(0));
        vm.assume(timestamp != 0);

        vm.prank(recorder);
        ledger.recordEvent(eventId, otherAggregateKey, PesaChainEventLedger.LoanEventType.REPAYMENT_RECORDED, 1, timestamp);
        PesaChainEventLedger.LoanEvent memory stored = ledger.getEvent(eventId);

        vm.expectRevert(PesaChainEventLedger.DuplicateEvent.selector);
        vm.prank(recorder);
        ledger.recordEvent(eventId, aggregateKey, PesaChainEventLedger.LoanEventType.REPAYMENT_RECORDED, 2, timestamp);

        PesaChainEventLedger.LoanEvent memory afterReplay = ledger.getEvent(eventId);
        assertEq(afterReplay.aggregateKey, stored.aggregateKey);
        assertEq(afterReplay.amountMinorUnits, stored.amountMinorUnits);
        assertEq(afterReplay.occurredAt, stored.occurredAt);
    }
}
