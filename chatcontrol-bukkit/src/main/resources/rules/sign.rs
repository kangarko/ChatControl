# -----------------------------------------------------------------------------------------------
# This file applies rules to signs and includes rules from global.rs
#
# For help, see https://docs.mineacademy.org/chatcontrol/rules
#
# PS: Use the {sign_lines} variable to get all sign lines, joined with a space.
# -----------------------------------------------------------------------------------------------

@import global

# Prevent string 'swag' on line on a sign.
#match \bswag\b
#then deny

# Prevents signs with "[Trade]" or "[Shop]" from being spied on via "/spy"
#match \[(Trade|Shop)\]
#dont spy
#dont log
#dont verbose