# ---------------------------------------------------------------------------------
# This file applies rules to proxy-level commands.
# Rules use regex to match commands and can deny, warn, log, or run actions.
#
# Uses the same syntax as the Bukkit rules/ files. For help, see:
# https://docs.mineacademy.org/chatcontrol/rules
#
# Available operators:
#   name <name>                          - Name this rule for identification
#   require sender perm <perm> [message] - Require sender to have a permission
#   require sender server <server>       - Require sender to be on a specific server
#   require sender script <script>       - Require JavaScript condition to be true
#   ignore sender perm <perm>            - Skip rule if sender has this permission
#   ignore sender server <server>        - Skip rule if sender is on this server
#   ignore sender script <script>        - Skip rule if JavaScript condition is true
#   require perm <perm> [message]        - Shorthand for require sender perm
#   ignore perm <perm>                   - Shorthand for ignore sender perm
#   require playedbefore                 - Only match if the sender has played before
#   ignore playedbefore                  - Skip rule if the sender has played before
#   strip colors <true/false>            - Strip colors before matching
#   strip accents <true/false>           - Strip accents before matching
#   then deny                            - Block the command
#   then warn <message>                  - Send a warning message to the sender
#   then command <command>               - Run a command as the sender
#   then proxy <command>                 - Run a command on the proxy console
#   then log <message>                   - Log a message to console
#   then kick <message>                  - Kick the player
#   then discord <channel> <message>     - Send a message to Discord
#   then write <file> <message>          - Write to a log file
#   then abort                           - Stop processing further rules
#   disabled                             - Temporarily disable this rule
# ---------------------------------------------------------------------------------

# Example: Prevent /op command on the proxy
#match ^/op\b
#ignore perm chatcontrol.bypass.rules
#name /op
#then warn &cThis command is not allowed.
#then deny

# Example: Log proxy-level punishment commands for auditing
#match ^/(ban|mute|kick|warn)\b
#name punishment-audit
#then log {player} executed proxy command: {original_message}
