// SPDX-License-Identifier: MIT
pragma solidity 0.8.28;

/// @notice Stores immutable, non-identifying evidence for selected PesaChain lifecycle events.
/// @dev This contract never holds funds and is not authoritative for operational loan state.
contract PesaChainEventLedger {
    enum LoanEventType {
        LOAN_APPROVED,
        LOAN_REJECTED,
        LOAN_DISBURSED,
        REPAYMENT_RECORDED,
        LOAN_COMPLETED
    }

    struct LoanEvent {
        bytes32 aggregateKey;
        LoanEventType eventType;
        uint256 amountMinorUnits;
        uint64 occurredAt;
        address recorder;
    }

    address public immutable owner;
    mapping(address => bool) public isRecorder;
    mapping(bytes32 => LoanEvent) private eventsById;

    event RecorderChanged(address indexed recorder, bool authorized);
    event LoanEventRecorded(
        bytes32 indexed eventId,
        bytes32 indexed aggregateKey,
        LoanEventType eventType,
        uint256 amountMinorUnits,
        uint64 occurredAt,
        address recorder
    );

    error Unauthorized();
    error InvalidInput();
    error DuplicateEvent();
    error UnexpectedAmount();

    constructor(address initialRecorder) {
        if (initialRecorder == address(0)) revert InvalidInput();
        owner = msg.sender;
        isRecorder[initialRecorder] = true;
        emit RecorderChanged(initialRecorder, true);
    }

    function setRecorder(address recorder, bool authorized) external {
        if (msg.sender != owner) revert Unauthorized();
        if (recorder == address(0)) revert InvalidInput();
        isRecorder[recorder] = authorized;
        emit RecorderChanged(recorder, authorized);
    }

    function recordEvent(
        bytes32 eventId,
        bytes32 aggregateKey,
        LoanEventType eventType,
        uint256 amountMinorUnits,
        uint64 occurredAt
    ) external {
        if (!isRecorder[msg.sender]) revert Unauthorized();
        if (eventId == bytes32(0) || aggregateKey == bytes32(0) || occurredAt == 0) revert InvalidInput();
        if (eventsById[eventId].recorder != address(0)) revert DuplicateEvent();

        bool amountRequired = eventType == LoanEventType.LOAN_DISBURSED
            || eventType == LoanEventType.REPAYMENT_RECORDED;
        if (amountRequired != (amountMinorUnits > 0)) revert UnexpectedAmount();

        eventsById[eventId] = LoanEvent(aggregateKey, eventType, amountMinorUnits, occurredAt, msg.sender);
        emit LoanEventRecorded(eventId, aggregateKey, eventType, amountMinorUnits, occurredAt, msg.sender);
    }

    function eventExists(bytes32 eventId) external view returns (bool) {
        return eventsById[eventId].recorder != address(0);
    }

    function getEvent(bytes32 eventId) external view returns (LoanEvent memory) {
        return eventsById[eventId];
    }

    function getEventDetails(bytes32 eventId) external view returns (
        bytes32 aggregateKey,
        uint8 eventType,
        uint256 amountMinorUnits,
        uint64 occurredAt,
        address recorder
    ) {
        LoanEvent storage recorded = eventsById[eventId];
        return (recorded.aggregateKey, uint8(recorded.eventType), recorded.amountMinorUnits, recorded.occurredAt, recorded.recorder);
    }
}
