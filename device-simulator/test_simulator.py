#!/usr/bin/env python3
"""
Unit tests for ESP32 Device Simulator.
Tests schema compatibility, event construction, idempotency, and state transitions
without connecting to an external MQTT broker.
"""

import json
import unittest
import uuid
from datetime import datetime, timedelta, timezone
from unittest.mock import MagicMock

from simulator import ESP32DeviceSimulator, get_utc_iso_timestamp


class TestESP32DeviceSimulator(unittest.TestCase):

    def setUp(self):
        self.sim = ESP32DeviceSimulator(
            broker="localhost",
            port=8883,
            username="esp32_vm001",
            password="dummy_password",
            client_id="esp32-test-client",
            machine_id="VM001",
            heartbeat_interval=30,
            motor_delay=0.01,
            scenario="SUCCESS",
            use_tls=False,
        )
        # Mock MQTT client publish
        self.sim.client.publish = MagicMock()
        self.sim.client.publish.return_value.mid = 1

    def test_utc_iso_timestamp_format(self):
        ts = get_utc_iso_timestamp()
        self.assertTrue(ts.endswith("Z"))
        # Must be parseable by ISO-8601 parser
        dt = datetime.fromisoformat(ts.replace("Z", "+00:00"))
        self.assertIsNotNone(dt)

    def test_heartbeat_payload_schema(self):
        payload = self.sim.build_heartbeat_payload()
        self.assertEqual(payload["machineId"], "VM001")
        self.assertEqual(payload["firmwareVersion"], "esp32-sim-1.0.0")
        self.assertIsInstance(payload["uptimeSeconds"], int)
        self.assertEqual(payload["wifiRssi"], -55)
        self.assertEqual(payload["mqttReconnectCount"], 0)
        self.assertIsInstance(payload["slots"], list)
        self.assertTrue(payload["timestamp"].endswith("Z"))

    def test_accepted_event_schema(self):
        cmd_id = str(uuid.uuid4())
        ord_id = str(uuid.uuid4())
        event = self.sim.build_accepted_event(cmd_id, ord_id, 1)

        self.assertIn("eventId", event)
        self.assertEqual(event["commandId"], cmd_id)
        self.assertEqual(event["machineId"], "VM001")
        self.assertEqual(event["orderId"], ord_id)
        self.assertEqual(event["eventType"], "COMMAND_ACCEPTED")
        self.assertEqual(event["slotNumber"], 1)
        self.assertTrue(event["timestamp"].endswith("Z"))
        # Omitted fields should not be present
        self.assertNotIn("result", event)
        self.assertNotIn("rejectionReason", event)

    def test_result_event_success_schema(self):
        cmd_id = str(uuid.uuid4())
        ord_id = str(uuid.uuid4())
        event = self.sim.build_result_event(cmd_id, ord_id, 1, "SUCCESS")

        self.assertEqual(event["eventType"], "DISPENSE_RESULT")
        self.assertEqual(event["result"], "SUCCESS")
        self.assertEqual(event["verificationLevel"], "MOTOR_CYCLE_ONLY")
        self.assertEqual(event["commandId"], cmd_id)
        self.assertEqual(event["orderId"], ord_id)
        self.assertEqual(event["slotNumber"], 1)
        self.assertNotIn("failureCode", event)
        self.assertNotIn("failureMessage", event)

    def test_result_event_failure_schema(self):
        cmd_id = str(uuid.uuid4())
        ord_id = str(uuid.uuid4())
        event = self.sim.build_result_event(cmd_id, ord_id, 2, "FAILURE")

        self.assertEqual(event["eventType"], "DISPENSE_RESULT")
        self.assertEqual(event["result"], "FAILURE")
        self.assertEqual(event["failureCode"], "MOTOR_TIMEOUT")
        self.assertIn("stall", event["failureMessage"])

    def test_result_event_uncertain_schema(self):
        cmd_id = str(uuid.uuid4())
        ord_id = str(uuid.uuid4())
        event = self.sim.build_result_event(cmd_id, ord_id, 1, "UNCERTAIN")

        self.assertEqual(event["eventType"], "DISPENSE_RESULT")
        self.assertEqual(event["result"], "UNCERTAIN")
        self.assertEqual(event["failureCode"], "POWER_GLITCH")

    def test_command_execution_flow(self):
        cmd_id = str(uuid.uuid4())
        ord_id = str(uuid.uuid4())
        cmd = {
            "commandId": cmd_id,
            "machineId": "VM001",
            "orderId": ord_id,
            "slotNumber": 1,
            "action": "DISPENSE",
            "createdAt": get_utc_iso_timestamp(),
            "expiresAt": (datetime.now(timezone.utc) + timedelta(minutes=1)).isoformat(),
            "parameters": {"motorRunMs": 10},
        }

        self.sim._handle_dispense_command(json.dumps(cmd))

        # Must have published 2 events: COMMAND_ACCEPTED and DISPENSE_RESULT
        self.assertEqual(self.sim.client.publish.call_count, 2)
        args_accepted = json.loads(self.sim.client.publish.call_args_list[0][0][1].decode("utf-8"))
        args_result = json.loads(self.sim.client.publish.call_args_list[1][0][1].decode("utf-8"))

        self.assertEqual(args_accepted["eventType"], "COMMAND_ACCEPTED")
        self.assertEqual(args_accepted["commandId"], cmd_id)

        self.assertEqual(args_result["eventType"], "DISPENSE_RESULT")
        self.assertEqual(args_result["result"], "SUCCESS")
        self.assertEqual(args_result["commandId"], cmd_id)

        # Command must be marked as processed
        self.assertIn(cmd_id, self.sim.processed_command_ids)

    def test_duplicate_command_rejected(self):
        cmd_id = str(uuid.uuid4())
        ord_id = str(uuid.uuid4())
        cmd = {
            "commandId": cmd_id,
            "machineId": "VM001",
            "orderId": ord_id,
            "slotNumber": 1,
        }

        # First run -> SUCCESS
        self.sim._handle_dispense_command(json.dumps(cmd))
        self.assertEqual(self.sim.client.publish.call_count, 2)

        # Second run with exact same commandId -> DUPLICATE_COMMAND rejection
        self.sim.client.publish.reset_mock()
        self.sim._handle_dispense_command(json.dumps(cmd))

        self.assertEqual(self.sim.client.publish.call_count, 1)
        rejected_event = json.loads(self.sim.client.publish.call_args_list[0][0][1].decode("utf-8"))
        self.assertEqual(rejected_event["eventType"], "COMMAND_REJECTED")
        self.assertEqual(rejected_event["rejectionReason"], "DUPLICATE_COMMAND")

    def test_expired_command_rejected(self):
        cmd_id = str(uuid.uuid4())
        ord_id = str(uuid.uuid4())
        cmd = {
            "commandId": cmd_id,
            "machineId": "VM001",
            "orderId": ord_id,
            "slotNumber": 1,
            "expiresAt": (datetime.now(timezone.utc) - timedelta(seconds=10)).isoformat(),
        }

        self.sim._handle_dispense_command(json.dumps(cmd))

        self.assertEqual(self.sim.client.publish.call_count, 1)
        rejected_event = json.loads(self.sim.client.publish.call_args_list[0][0][1].decode("utf-8"))
        self.assertEqual(rejected_event["eventType"], "COMMAND_REJECTED")
        self.assertEqual(rejected_event["rejectionReason"], "COMMAND_EXPIRED")

    def test_invalid_slot_rejected(self):
        cmd_id = str(uuid.uuid4())
        ord_id = str(uuid.uuid4())
        cmd = {
            "commandId": cmd_id,
            "machineId": "VM001",
            "orderId": ord_id,
            "slotNumber": 0,  # Invalid slot number
        }

        self.sim._handle_dispense_command(json.dumps(cmd))

        self.assertEqual(self.sim.client.publish.call_count, 1)
        rejected_event = json.loads(self.sim.client.publish.call_args_list[0][0][1].decode("utf-8"))
        self.assertEqual(rejected_event["eventType"], "COMMAND_REJECTED")
        self.assertEqual(rejected_event["rejectionReason"], "SLOT_INVALID")

    def test_event_ack_handling(self):
        ack_id = str(uuid.uuid4())
        evt_id = str(uuid.uuid4())
        cmd_id = str(uuid.uuid4())
        ack_msg = {
            "ackId": ack_id,
            "eventId": evt_id,
            "commandId": cmd_id,
            "machineId": "VM001",
            "status": "PROCESSED",
            "timestamp": get_utc_iso_timestamp(),
        }

        self.sim._handle_event_ack(json.dumps(ack_msg))
        self.assertIn(evt_id, self.sim.acknowledged_event_ids)


if __name__ == "__main__":
    unittest.main()

