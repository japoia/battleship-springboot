package com.example.battleship.web;

import com.example.battleship.service.GameService;
import com.example.battleship.service.dto.FireRequest;
import com.example.battleship.service.dto.FireResponse;
import com.example.battleship.service.dto.GameStateResponse;
import com.example.battleship.service.dto.PlaceShipRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/game")
public final class GameController {
  private final GameService game;

  public GameController(GameService game) {
    this.game = game;
  }

  @PostMapping("/new")
  public ResponseEntity<GameStateResponse> newGame(HttpSession session) {
    try {
      return ResponseEntity.ok(game.newGame(session));
    } catch (Exception e) {
      return ResponseEntity.internalServerError().build();
    }
  }

  @GetMapping("/state")
  public ResponseEntity<GameStateResponse> state(HttpSession session) {
    try {
      return ResponseEntity.ok(game.state(session));
    } catch (Exception e) {
      return ResponseEntity.internalServerError().build();
    }
  }

  @PostMapping("/place")
  public ResponseEntity<GameStateResponse> place(@Valid @RequestBody PlaceShipRequest req, HttpSession session) {
    try {
      return ResponseEntity.ok(game.placePlayerShip(session, req));
    } catch (Exception e) {
      return ResponseEntity.badRequest().build();
    }
  }

  @PostMapping("/auto-place")
  public ResponseEntity<GameStateResponse> autoPlace(HttpSession session) {
    try {
      return ResponseEntity.ok(game.autoPlaceRemainingForPlayer(session));
    } catch (Exception e) {
      return ResponseEntity.badRequest().build();
    }
  }

  @PostMapping("/start")
  public ResponseEntity<GameStateResponse> start(HttpSession session) {
    try {
      return ResponseEntity.ok(game.startPlay(session));
    } catch (Exception e) {
      return ResponseEntity.badRequest().build();
    }
  }

  @PostMapping("/fire")
  public ResponseEntity<FireResponse> fire(@Valid @RequestBody FireRequest req, HttpSession session) {
    try {
      return ResponseEntity.ok(game.playerFires(session, req));
    } catch (Exception e) {
      return ResponseEntity.badRequest().build();
    }
  }
}

