/// Disabled home-feed account ids consulted by [QuackerTwitterClient.fetch].
///
/// When non-empty, rotation prefers accounts that still participate in For you /
/// Following home feeds, so turning an account off stops burning it on Following
/// search chunks too. Falls back to the full pool if every account is excluded
/// (comments / quotes / profiles still need *someone*).
class AccountFetchGate {
  static Set<String> disabledIds = {};
}
