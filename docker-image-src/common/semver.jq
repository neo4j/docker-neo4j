def _semver_obj2obj($req):
  if   . == $req  then true
  elif .major != $req.major and .major != "x" and .major != "*" then false
  elif .minor != $req.minor and .minor != "x" and .minor != "*" then false
  elif .patch != $req.patch and .patch != "x" and .patch != "*"   then false
  elif $req.minor == null and ( .minor == "x" or .minor == "*" ) then false
  elif $req.patch == null and ( .patch == "x" or .patch == "*" ) then false
  elif $req.major == null and $req.minor == null and $req.patch == null  then false
  else true end;

def _ver2obj:
  if   type == "object" then .
  elif type == "string" and test("(?<major>[0-9x*]+)(\\.(?<minor>[0-9x*]+))?(\\.?(?<patch>[0-9x*]+))?") then capture("(?<major>[0-9x*]+)(\\.(?<minor>[0-9x*]+))?(\\.?(?<patch>[0-9x*]+))?")
  elif type == "string" and . == "" then {major: null, minor:null, patch:null}
  elif type == "number" then {minor:floor,patch:(.-floor)}
  else {major: .} end;

# Returns true if input version spec semantically matches the requested version
def semver($req):
  if $req == null or $req == "" then false
  elif . == $req then true
  else _ver2obj|_semver_obj2obj($req|_ver2obj) end;
