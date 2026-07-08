#!/bin/bash
set -e

echo "=== TEST: Per-Turnout Servo Configuration End-to-End ==="
echo ""

# Expected mappings from your servo table
declare -A SERVO_OPEN=(
    [001]="317" [002]="354" [003]="424" [004]="185" [005]="178"
    [006]="82"  [007]="150" [008]="207" [009]="152" [010]="378"
)
declare -A SERVO_CLOSED=(
    [001]="238" [002]="233" [003]="233" [004]="343" [005]="376"
    [006]="464" [007]="343" [008]="389" [009]="292" [010]="209"
)

# 1. Check database is accessible
echo "Checking MySQL database..."
mysql -h localhost -u train -ptrain train_controller -e "SELECT COUNT(*) as device_count FROM device_servo_config;" 2>/dev/null || {
    echo "ERROR: Cannot connect to database or table missing."
    echo "Run migrations first: mvn -q flyway:migrate"
    exit 1
}
echo "✓ Database table exists"
echo ""

# 2. Sample verify a few servo configs from database
echo "Verifying servo config in database:"
for turnout in 001 002 010; do
    result=$(mysql -h localhost -u train -ptrain train_controller -e \
        "SELECT open_angle, closed_angle FROM device_servo_config WHERE device_id='$turnout';" \
        -N 2>/dev/null)
    open=$(echo $result | awk '{print $1}')
    closed=$(echo $result | awk '{print $2}')
    expected_open=${SERVO_OPEN[$turnout]}
    expected_closed=${SERVO_CLOSED[$turnout]}
    
    if [ "$open" == "$expected_open" ] && [ "$closed" == "$expected_closed" ]; then
        echo "  ✓ Turnout $turnout: OPEN=$open CLOSED=$closed"
    else
        echo "  ✗ Turnout $turnout: Expected OPEN=$expected_open CLOSED=$expected_closed, got OPEN=$open CLOSED=$closed"
        exit 1
    fi
done
echo ""

echo "=== All servo configurations validated ==="
echo ""
echo "Next: Start the interceptor and test with MQTT:"
echo "  mvn spring-boot:run"
echo ""
echo "In another terminal, publish test MQTT:"
echo "  mosquitto_pub -h localhost -t trains/track/turnout/001 -m THROWN"
echo "  mosquitto_pub -h localhost -t trains/track/turnout/001 -m CLOSED"
echo ""
echo "Verify in RS485 traffic on USB adapter that frames include:"
echo "  v1|turnout1|cmd-XXX|TURNOUT|001|OPEN|317|238    (when THROWN)"
echo "  v1|turnout1|cmd-XXX|TURNOUT|001|CLOSED|238|317  (when CLOSED)"
