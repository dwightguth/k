// Copyright (c) 2014 K Team. All Rights Reserved.
require "../../kernelc.k"

module COMM-ASSOC-SPEC
  imports KERNELC

rule
  <fun>... FUN:Map ...</fun>
  <k>
    comm_assoc(tv(int, X:MInt{bitwidth(32)}), tv(int, Y:MInt{bitwidth(32)}), tv(int, Z:MInt{bitwidth(32)}))
  =>
    tv(int, mi(32, 1))
  ...</k>

endmodule
