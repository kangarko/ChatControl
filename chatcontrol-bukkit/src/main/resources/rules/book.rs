# -----------------------------------------------------------------------------------------------
# This file applies rules to book pages and titles and includes rules from global.rs.
#
# For help, see https://docs.mineacademy.org/chatcontrol/rules
# -----------------------------------------------------------------------------------------------

@import global

# Prevent message containing 'Herobrine' in title or a page in the book.
#match \bHerobrine\b
#then warn {prefix_warn} You cannot write Herobrine in books!
#then deny