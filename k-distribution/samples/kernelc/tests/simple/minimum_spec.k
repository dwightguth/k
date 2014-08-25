// Copyright (c) 2014 K Team. All Rights Reserved.
require "../../kernelc.k"

module MINIMUM-SPEC
  imports KERNELC

rule
  <fun>... FUN:Map ...</fun>
  <k>
    minimum(tv(int, X:MInt{bitwidth(32)}), tv(int, Y:MInt{bitwidth(32)}))
  =>
    tv(int, sMinMInt(X, Y))
  ...</k> 
endmodule
