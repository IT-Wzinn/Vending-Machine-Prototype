#!/usr/bin/env python3
"""
ESP32 Hardware Device Simulator for Vending Machine Prototype.
Simulates hardware interactions (dispense motor, drop sensor, heartbeat)
over MQTT over TLS according to docs/MQTT_CONTRACT.md and Backend DTOs.
"""

import argparse
import json
import logging
import os
import signal
import ssl
import sys
import threading
import time
import uuid
from datetime import datetime, timezone
from typing import Any, Dict, Optional, Set

try:
    from dotenv import load_dotenv
    load_dotenv()
except ImportError:
    pass

import paho.mqtt.client as mqtt

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger("ESP32Simulator")


def get_utc_iso_timestamp() -> str:
    """Returns current UTC timestamp in ISO-8601 format (matching Java Instant.toString())."""
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


class ESP32DeviceSimulator:
    """Simulates an ESP32 microcontroller controlling a vending machine."""

    def __init__(
        self,
        broker: str,
        port: int,
        username: str,
        password: str,
        client_id: str,
        machine_id: str,
        heartbeat_interval: int = 30,
        motor_delay: float = 2.0,
        scenario: str = "SUCCESS",
        use_tls: bool = True,
    ):
        self.broker = broker
        self.port = port
        self.username = username
        self.password = password
        self.client_id = client_id
        self.machine_id = machine_id
        self.heartbeat_interval = heartbeat_interval
        self.motor_delay = motor_delay
        self.scenario = scenario.upper()
        self.use_tls = use_tls

        # State tracking
        self.start_time = time.time()
        self.reconnect_count = 0
        self.is_connected = False
        self.running = False

        # In-memory NVS storage simulation for idempotency and status
        self.processed_command_ids: Set[str] = set()
        self.acknowledged_event_ids: Set[str] = set()
        self.slot_status: Dict[int, Dict[str, Any]] = {
            1: {"lastDispenseResult": None, "lastDispenseAt": None}
        }

        # Topics
        self.topic_commands = f"vending/{self.machine_id}/commands"
        self.topic_events_ack = f"vending/{self.machine_id}/events/ack"
        self.topic_heartbeat = f"vending/{self.machine_id}/heartbeat"
        self.topic_events = f"vending/{self.machine_id}/events"

        # MQTT Client initialization (compatible with paho-mqtt 1.x and 2.x)
        if hasattr(mqtt, "CallbackAPIVersion"):
            self.client = mqtt.Client(
                mqtt.CallbackAPIVersion.VERSION2,
                client_id=self.client_id,
                clean_session=True,  # Contract §2.3 mandates cleanSession=true for ESP32
            )
        else:
            self.client = mqtt.Client(
                client_id=self.client_id,
                clean_session=True,
            )

        if self.username:
            self.client.username_pw_set(self.username, self.password)

        if self.use_tls:
            context = ssl.create_default_context()
            self.client.tls_set_context(context)

        # Wire callbacks
        self.client.on_connect = self._on_connect
        self.client.on_disconnect = self._on_disconnect
        self.client.on_message = self._on_message

        # Background threads
        self._heartbeat_thread: Optional[threading.Thread] = None

    def start(self):
        """Connects to the broker and starts heartbeat and message processing loops."""
        self.running = True
        logger.info(
            "[SIMULATOR] Starting ESP32 Simulator for Machine '%s' (Client ID: '%s', Scenario: '%s')",
            self.machine_id,
            self.client_id,
            self.scenario,
        )
        logger.info(
            "[SIMULATOR] Connecting to broker %s:%d (TLS=%s)...",
            self.broker,
            self.port,
            self.use_tls,
        )

        try:
            self.client.connect(self.broker, self.port, keepalive=60)
            self.client.loop_start()
        except Exception as e:
            logger.error("[SIMULATOR] Connection attempt failed: %s", e)
            raise

        # Start periodic heartbeat thread
        self._heartbeat_thread = threading.Thread(
            target=self._heartbeat_loop, name="HeartbeatWorker", daemon=True
        )
        self._heartbeat_thread.start()

    def stop(self):
        """Gracefully disconnects and stops background tasks."""
        logger.info("[SIMULATOR] Shutting down ESP32 simulator...")
        self.running = False
        try:
            self.client.loop_stop()
            self.client.disconnect()
        except Exception as e:
            logger.warning("[SIMULATOR] Error during disconnect: %s", e)
        logger.info("[SIMULATOR] Simulator stopped successfully.")

    # ─────────────────────────────────────────────────────────────────────────
    # MQTT Callbacks
    # ─────────────────────────────────────────────────────────────────────────

    def _on_connect(self, client, userdata, flags, rc, properties=None):
        """Callback when connection to broker is established."""
        reason_code = rc if isinstance(rc, int) else getattr(rc, "value", rc)
        if reason_code == 0:
            self.is_connected = True
            logger.info(
                "[MQTT CONNECTED] Connected successfully to EMQX broker %s:%d",
                self.broker,
                self.port,
            )

            # Subscribe to commands and events ACK
            self.client.subscribe(self.topic_commands, qos=1)
            self.client.subscribe(self.topic_events_ack, qos=1)
            logger.info(
                "[MQTT SUBSCRIBED] Subscribed to '%s' (QoS 1) and '%s' (QoS 1)",
                self.topic_commands,
                self.topic_events_ack,
            )

            # Send an immediate heartbeat upon connection
            self.publish_heartbeat()
        else:
            logger.error(
                "[MQTT CONNECT ERROR] Connection failed with reason code: %s",
                reason_code,
            )

    def _on_disconnect(self, client, userdata, disconnect_flags_or_rc, reason_code=None, properties=None):
        """Callback when disconnected from broker."""
        self.is_connected = False
        self.reconnect_count += 1
        rc = reason_code if reason_code is not None else disconnect_flags_or_rc
        logger.warning(
            "[MQTT DISCONNECTED] Disconnected from broker (rc=%s). Total reconnects: %d",
            rc,
            self.reconnect_count,
        )

    def _on_message(self, client, userdata, message):
        """Dispatches incoming MQTT messages on subscribed topics."""
        topic = message.topic
        try:
            payload_str = message.payload.decode("utf-8")
        except UnicodeDecodeError:
            logger.error("[MQTT PAYLOAD ERROR] Received non-UTF8 payload on %s", topic)
            return

        if topic == self.topic_commands:
            # Handle in a worker thread to prevent blocking Paho network loop during motor delay
            threading.Thread(
                target=self._handle_dispense_command,
                args=(payload_str,),
                daemon=True,
            ).start()
        elif topic == self.topic_events_ack:
            self._handle_event_ack(payload_str)
        else:
            logger.debug("[MQTT UNHANDLED] Message on topic '%s': %s", topic, payload_str)

    # ─────────────────────────────────────────────────────────────────────────
    # Heartbeat Routine
    # ─────────────────────────────────────────────────────────────────────────

    def _heartbeat_loop(self):
        """Periodically publishes heartbeat every heartbeat_interval seconds."""
        logger.info(
            "[HEARTBEAT LOOP] Heartbeat thread started. Interval: %ds",
            self.heartbeat_interval,
        )
        while self.running:
            time.sleep(1)
            # Evaluate interval
            if int(time.time() - self.start_time) % self.heartbeat_interval == 0:
                if self.is_connected:
                    self.publish_heartbeat()

    def build_heartbeat_payload(self) -> Dict[str, Any]:
        """Constructs HeartbeatMessage matching Backend schema."""
        uptime = int(time.time() - self.start_time)
        slots_list = []
        for slot_num, data in self.slot_status.items():
            if data["lastDispenseResult"] is not None:
                slots_list.append(
                    {
                        "slotNumber": slot_num,
                        "lastDispenseResult": data["lastDispenseResult"],
                        "lastDispenseAt": data["lastDispenseAt"],
                    }
                )

        return {
            "machineId": self.machine_id,
            "firmwareVersion": "esp32-sim-1.0.0",
            "uptimeSeconds": uptime,
            "wifiRssi": -55,
            "mqttReconnectCount": self.reconnect_count,
            "slots": slots_list,
            "timestamp": get_utc_iso_timestamp(),
        }

    def publish_heartbeat(self):
        """Publishes heartbeat message (QoS 0, retain=false)."""
        payload = self.build_heartbeat_payload()
        try:
            payload_bytes = json.dumps(payload).encode("utf-8")
            self.client.publish(self.topic_heartbeat, payload_bytes, qos=0, retain=False)
            logger.info(
                "[HEARTBEAT] Published to '%s': uptime=%ds, reconnects=%d, slots=%d",
                self.topic_heartbeat,
                payload["uptimeSeconds"],
                payload["mqttReconnectCount"],
                len(payload["slots"]),
            )
        except Exception as e:
            logger.error("[HEARTBEAT ERROR] Failed to publish heartbeat: %s", e)

    # ─────────────────────────────────────────────────────────────────────────
    # Dispense Command Execution
    # ─────────────────────────────────────────────────────────────────────────

    def _handle_dispense_command(self, payload_str: str):
        """Processes an incoming DispenseCommandMessage."""
        logger.info("[COMMAND RECEIVED] Raw payload: %s", payload_str)
        try:
            cmd = json.loads(payload_str)
        except json.JSONDecodeError as e:
            logger.error("[COMMAND ERROR] Invalid JSON payload: %s", e)
            return

        command_id = cmd.get("commandId")
        machine_id = cmd.get("machineId")
        order_id = cmd.get("orderId")
        slot_number = cmd.get("slotNumber")
        expires_at_str = cmd.get("expiresAt")
        parameters = cmd.get("parameters") or {}

        # 1. Basic validation
        if not command_id or not order_id:
            logger.error("[COMMAND REJECTED] Missing commandId or orderId")
            return

        if machine_id and machine_id != self.machine_id:
            logger.warning(
                "[COMMAND IGNORED] MachineId '%s' does not match '%s'",
                machine_id,
                self.machine_id,
            )
            return

        if slot_number is None or slot_number < 1:
            logger.warning("[COMMAND REJECTED] Invalid slotNumber: %s", slot_number)
            self.publish_event(
                self.build_rejected_event(
                    command_id=command_id,
                    order_id=order_id,
                    slot_number=slot_number,
                    reason="SLOT_INVALID",
                )
            )
            return

        # 2. Idempotency Check (NVS duplicate detection)
        if command_id in self.processed_command_ids:
            logger.warning(
                "[COMMAND DUPLICATE] commandId '%s' was already processed! Rejecting.",
                command_id,
            )
            self.publish_event(
                self.build_rejected_event(
                    command_id=command_id,
                    order_id=order_id,
                    slot_number=slot_number,
                    reason="DUPLICATE_COMMAND",
                )
            )
            return

        # 3. Expiration Check
        if expires_at_str:
            try:
                # Parse ISO-8601
                expires_at = datetime.fromisoformat(expires_at_str.replace("Z", "+00:00"))
                if datetime.now(timezone.utc) > expires_at:
                    logger.warning(
                        "[COMMAND EXPIRED] commandId '%s' expired at %s",
                        command_id,
                        expires_at_str,
                    )
                    self.publish_event(
                        self.build_rejected_event(
                            command_id=command_id,
                            order_id=order_id,
                            slot_number=slot_number,
                            reason="COMMAND_EXPIRED",
                        )
                    )
                    return
            except Exception as e:
                logger.warning("[COMMAND WARNING] Failed to parse expiresAt '%s': %s", expires_at_str, e)

        # 4. Send COMMAND_ACCEPTED event
        logger.info(
            "[DISPENSE IN PROGRESS] Command %s valid. Slot: %d, Action: %s. Sending COMMAND_ACCEPTED.",
            command_id,
            slot_number,
            cmd.get("action", "DISPENSE"),
        )
        accepted_event = self.build_accepted_event(
            command_id=command_id, order_id=order_id, slot_number=slot_number
        )
        self.publish_event(accepted_event)

        # 5. Simulate Physical Motor Run Delay
        motor_run_ms = parameters.get("motorRunMs")
        actual_delay = (motor_run_ms / 1000.0) if motor_run_ms else self.motor_delay
        logger.info(
            "[MOTOR] Running motor for Slot %d (simulating %.1fs execution)...",
            slot_number,
            actual_delay,
        )
        time.sleep(actual_delay)

        # 6. Determine Scenario & Send DISPENSE_RESULT event
        result_event = self.build_result_event(
            command_id=command_id,
            order_id=order_id,
            slot_number=slot_number,
            scenario=self.scenario,
        )
        self.publish_event(result_event)

        # 7. Record as completed in local storage
        self.processed_command_ids.add(command_id)
        self.slot_status[slot_number] = {
            "lastDispenseResult": result_event.get("result"),
            "lastDispenseAt": result_event.get("timestamp"),
        }
        logger.info(
            "[DISPENSE FINISHED] Dispense cycle complete for commandId=%s, result=%s",
            command_id,
            result_event.get("result"),
        )

    # ─────────────────────────────────────────────────────────────────────────
    # Event Builders (Matching DeviceEventMessage.java)
    # ─────────────────────────────────────────────────────────────────────────

    def build_accepted_event(
        self, command_id: str, order_id: str, slot_number: int
    ) -> Dict[str, Any]:
        """Builds DeviceEventMessage for COMMAND_ACCEPTED."""
        return {
            "eventId": str(uuid.uuid4()),
            "commandId": command_id,
            "machineId": self.machine_id,
            "orderId": order_id,
            "eventType": "COMMAND_ACCEPTED",
            "slotNumber": slot_number,
            "timestamp": get_utc_iso_timestamp(),
        }

    def build_rejected_event(
        self, command_id: str, order_id: str, slot_number: Optional[int], reason: str
    ) -> Dict[str, Any]:
        """Builds DeviceEventMessage for COMMAND_REJECTED."""
        return {
            "eventId": str(uuid.uuid4()),
            "commandId": command_id,
            "machineId": self.machine_id,
            "orderId": order_id,
            "eventType": "COMMAND_REJECTED",
            "slotNumber": slot_number,
            "rejectionReason": reason,
            "timestamp": get_utc_iso_timestamp(),
        }

    def build_result_event(
        self,
        command_id: str,
        order_id: str,
        slot_number: int,
        scenario: str,
    ) -> Dict[str, Any]:
        """Builds DeviceEventMessage for DISPENSE_RESULT (SUCCESS, FAILURE, UNCERTAIN)."""
        event = {
            "eventId": str(uuid.uuid4()),
            "commandId": command_id,
            "machineId": self.machine_id,
            "orderId": order_id,
            "eventType": "DISPENSE_RESULT",
            "slotNumber": slot_number,
            "verificationLevel": "MOTOR_CYCLE_ONLY",
            "timestamp": get_utc_iso_timestamp(),
        }

        if scenario == "FAILURE":
            event["result"] = "FAILURE"
            event["failureCode"] = "MOTOR_TIMEOUT"
            event["failureMessage"] = "Simulated motor stall or drop sensor timeout"
        elif scenario == "UNCERTAIN":
            event["result"] = "UNCERTAIN"
            event["failureCode"] = "POWER_GLITCH"
            event["failureMessage"] = "Simulated MCU reset during motor actuation"
        else:  # Default to SUCCESS
            event["result"] = "SUCCESS"

        return event

    def publish_event(self, event_dict: Dict[str, Any]):
        """Publishes a DeviceEventMessage to vending/{machine_id}/events (QoS 1)."""
        try:
            payload_bytes = json.dumps(event_dict).encode("utf-8")
            info = self.client.publish(self.topic_events, payload_bytes, qos=1, retain=False)
            logger.info(
                "[EVENT PUBLISHED] type=%s, eventId=%s, commandId=%s -> topic='%s' (QoS 1, mid=%s)",
                event_dict.get("eventType"),
                event_dict.get("eventId"),
                event_dict.get("commandId"),
                self.topic_events,
                info.mid,
            )
        except Exception as e:
            logger.error("[EVENT PUBLISH ERROR] Failed to publish event: %s", e)

    # ─────────────────────────────────────────────────────────────────────────
    # Event ACK Handling
    # ─────────────────────────────────────────────────────────────────────────

    def _handle_event_ack(self, payload_str: str):
        """Processes an incoming EventAckMessage from Backend."""
        try:
            ack = json.loads(payload_str)
        except json.JSONDecodeError as e:
            logger.error("[ACK ERROR] Failed to deserialize EventAckMessage: %s", e)
            return

        ack_id = ack.get("ackId")
        event_id = ack.get("eventId")
        command_id = ack.get("commandId")
        status = ack.get("status", "PROCESSED")

        if event_id:
            self.acknowledged_event_ids.add(event_id)

        logger.info(
            "[ACK RECEIVED] Backend acknowledged eventId=%s for commandId=%s (ackId=%s, status=%s)",
            event_id,
            command_id,
            ack_id,
            status,
        )


def parse_arguments():
    """Parses command-line arguments and merges with environment variables."""
    parser = argparse.ArgumentParser(
        description="ESP32 Hardware Device Simulator for Vending Machine Prototype"
    )
    parser.add_argument(
        "--broker",
        default=os.getenv("MQTT_BROKER", "f18d138e.ala.asia-southeast1.emqxsl.com"),
        help="MQTT broker hostname",
    )
    parser.add_argument(
        "--port",
        type=int,
        default=int(os.getenv("MQTT_PORT", "8883")),
        help="MQTT broker port (default 8883 for TLS)",
    )
    parser.add_argument(
        "--username",
        default=os.getenv("MQTT_USERNAME", "esp32_vm001"),
        help="MQTT username",
    )
    parser.add_argument(
        "--password",
        default=os.getenv("MQTT_PASSWORD", ""),
        help="MQTT password (recommended to set in .env)",
    )
    parser.add_argument(
        "--client-id",
        default=os.getenv("MQTT_CLIENT_ID", "esp32-vm001-sim"),
        help="MQTT client ID (default esp32-vm001-sim)",
    )
    parser.add_argument(
        "--machine-id",
        default=os.getenv("MACHINE_ID", "VM001"),
        help="Vending machine ID (default VM001)",
    )
    parser.add_argument(
        "--scenario",
        "-s",
        choices=["SUCCESS", "FAILURE", "UNCERTAIN"],
        default=os.getenv("SIMULATOR_SCENARIO", "SUCCESS").upper(),
        help="Simulation scenario (default SUCCESS)",
    )
    parser.add_argument(
        "--heartbeat-interval",
        type=int,
        default=int(os.getenv("HEARTBEAT_INTERVAL", "30")),
        help="Heartbeat publish interval in seconds (default 30)",
    )
    parser.add_argument(
        "--motor-delay",
        type=float,
        default=float(os.getenv("MOTOR_DELAY_SECONDS", "2.0")),
        help="Simulated motor execution delay in seconds (default 2.0)",
    )
    parser.add_argument(
        "--no-tls",
        action="store_true",
        default=os.getenv("MQTT_TLS", "true").lower() == "false",
        help="Disable TLS (use plain TCP)",
    )
    parser.add_argument(
        "--mock-test",
        action="store_true",
        help="Run self-validation mock test without connecting to broker",
    )
    return parser.parse_args()


def main():
    args = parse_arguments()

    if args.mock_test:
        logger.info("[MOCK TEST] Running self-test mode...")
        sim = ESP32DeviceSimulator(
            broker=args.broker,
            port=args.port,
            username=args.username,
            password=args.password,
            client_id=args.client_id,
            machine_id=args.machine_id,
            motor_delay=0.1,
            scenario=args.scenario,
        )
        # Test heartbeat generation
        hb = sim.build_heartbeat_payload()
        assert hb["machineId"] == args.machine_id
        assert "uptimeSeconds" in hb
        logger.info("[MOCK TEST] Heartbeat schema verified: %s", json.dumps(hb))

        # Test command accepted event
        cmd_id = str(uuid.uuid4())
        ord_id = str(uuid.uuid4())
        acc = sim.build_accepted_event(cmd_id, ord_id, 1)
        assert acc["eventType"] == "COMMAND_ACCEPTED"
        assert acc["commandId"] == cmd_id
        logger.info("[MOCK TEST] Command Accepted event verified: %s", json.dumps(acc))

        # Test result event
        res = sim.build_result_event(cmd_id, ord_id, 1, args.scenario)
        assert res["eventType"] == "DISPENSE_RESULT"
        assert res["result"] == args.scenario
        logger.info("[MOCK TEST] Dispense Result event verified: %s", json.dumps(res))

        logger.info("[MOCK TEST] Self-test completed successfully!")
        return

    # Check password
    if not args.password:
        logger.error(
            "[CONFIG ERROR] MQTT password is required. Please set MQTT_PASSWORD in .env or pass --password."
        )
        sys.exit(1)

    simulator = ESP32DeviceSimulator(
        broker=args.broker,
        port=args.port,
        username=args.username,
        password=args.password,
        client_id=args.client_id,
        machine_id=args.machine_id,
        heartbeat_interval=args.heartbeat_interval,
        motor_delay=args.motor_delay,
        scenario=args.scenario,
        use_tls=not args.no_tls,
    )

    # Register graceful shutdown signals
    def shutdown_handler(signum, frame):
        logger.info("[SIGNAL] Received termination signal (%s)", signum)
        simulator.stop()
        sys.exit(0)

    signal.signal(signal.SIGINT, shutdown_handler)
    signal.signal(signal.SIGTERM, shutdown_handler)

    simulator.start()

    logger.info(
        "[READY] ESP32 Device Simulator running. Press Ctrl+C to exit. Listening on topics:"
    )
    logger.info("  -> Subscribe commands:  %s", simulator.topic_commands)
    logger.info("  -> Subscribe ack:       %s", simulator.topic_events_ack)
    logger.info("  <- Publish heartbeat:   %s", simulator.topic_heartbeat)
    logger.info("  <- Publish events:      %s", simulator.topic_events)

    # Keep main thread alive
    try:
        while simulator.running:
            time.sleep(1)
    except KeyboardInterrupt:
        logger.info("[EXIT] Keyboard interrupt received.")
    finally:
        simulator.stop()


if __name__ == "__main__":
    main()
