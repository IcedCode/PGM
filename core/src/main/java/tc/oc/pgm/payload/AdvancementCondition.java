package tc.oc.pgm.payload;

// Conditions required for a payload to advance
enum AdvancementCondition {
  EXCLUSIVE, // Team owns all players on the point
  MAJORITY, // Team owns more than half the players on the point
  LEAD // Team owns more players on the point than any other single team
}
