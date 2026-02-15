# -----------------------------------------------------------------------------------------------
# This file applies rules to outgoing chat packets sent from the server to the player.
# Use this to modify or hide messages from other plugins (e.g. WorldGuard, server messages).
#
# Requires ProtocolLib to be installed and ProtocolLib.Enabled set to true in settings.yml.
# You must also add 'packet' to the Rules.Apply_On list in settings.yml.
#
# WARNING: Packet rules intercept ALL outgoing chat messages, including those from ChatControl
# itself (channels, formats, etc.). Write your match patterns carefully to only target messages
# from other plugins. Use 'Rules.Verbose: true' in settings.yml for debugging.
#
# For help, see https://docs.mineacademy.org/chatcontrol/rules
# -----------------------------------------------------------------------------------------------

# Example: Change WorldGuard's item drop deny message.
#match Hey! Sorry, but you can't drop items here\.
#then rewrite &cYou cannot drop items in this area.

# Example: Hide a specific plugin message from appearing in chat.
#match Some annoying plugin message
#then deny

# Example: Replace text in server messages.
#match Welcome to the server
#then rewrite &aWelcome to &bMy Awesome Server&a!
