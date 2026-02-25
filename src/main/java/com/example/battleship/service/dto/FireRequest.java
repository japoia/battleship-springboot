package com.example.battleship.service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public final class FireRequest {
  @Min(0) @Max(9)
  public int x;

  @Min(0) @Max(9)
  public int y;
}

