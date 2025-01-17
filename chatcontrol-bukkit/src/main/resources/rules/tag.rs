# -----------------------------------------------------------------------------------------------
# This file manages rules applying for player tags: nick, prefix and suffix
# and includes rules from global.rs.
#
# Permissions required for color usage:
# chatcontrol.color.{color_name}
# chatcontrol.hexcolor.{color_name}
#
# To fine tune control over color usage in nicknames, 
# prefixes or suffixes, see the rule examples below.
# -----------------------------------------------------------------------------------------------

@import global

# Prevent certain words being used as nicks.
# This example blocks "Notch" & "Herobrine"
#match ^(Notch|Herobrine)$
#require tag nick
#strip colors true
#then warn {prefix_error} This nickname is not allowed!
#then deny

# Prevent players writing variables to their tags.
match [{%][^{}]+[}%]
then warn {prefix_error} You cannot use variables in your tag!
then deny
