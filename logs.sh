#!/bin/bash
ssh -i ~/.ssh/key.pem root@43.157.251.205 "journalctl -u umkmshop -f"
