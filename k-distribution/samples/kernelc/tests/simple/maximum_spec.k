// Copyright (c) 2014 K Team. All Rights Reserved.
require "../../kernelc.k"

module MAXIMUM-SPEC
  imports KERNELC

rule
  <fun>... FUN:Map ...</fun>
  <k>
    maximum(tv(int, X:MInt{bitwidth(32)}), tv(int, Y:MInt{bitwidth(32)}))
  =>
    tv(int, sMaxMInt(X, Y))
  ...</k>

endmodule
