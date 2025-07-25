#!/bin/bash

# Create a test script to test the repair mini-game
{
    echo "new"
    sleep 1
    echo "testuser"
    sleep 1
    echo "TestUser"
    sleep 1
    echo "1"
    sleep 2
    echo "move north"
    sleep 1
    echo "repair 1 0"
    sleep 2
    echo ""  # First space bar press
    sleep 1
    echo ""  # Second space bar press
    sleep 1
    echo ""  # Third space bar press
    sleep 2
    echo "exit"
} | telnet localhost 8181