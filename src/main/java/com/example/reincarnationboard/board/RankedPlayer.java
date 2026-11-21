package com.example.reincarnationboard.board;

import java.util.UUID;

/** 参与排行的一名玩家。 */
public record RankedPlayer(UUID id, String name, int score) {
}
