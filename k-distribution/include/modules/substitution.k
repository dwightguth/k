// Copyright (c) 2012-2014 K Team. All Rights Reserved.
require "k-prelude.k"

module SUBSTITUTION
  imports MAP

  syntax K ::= K "[" K "/" K "]"  [function, hook(Substitution:userSubstitution)]

endmodule
