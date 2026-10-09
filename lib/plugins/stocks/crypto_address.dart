/// Recognising a pasted token contract address, and naming the networks one
/// lives on.
///
/// Two families cover what readers paste from X: EVM chains (Ethereum, Base,
/// BNB Chain, Arbitrum, Polygon, …) share one `0x` + 40 hex address format, so
/// the same string can exist on several networks; Solana mints are base58.
library;

enum CryptoAddressKind { evm, solana }

/// A pasted string that is shaped like a contract address.
class CryptoAddressInput {
  final CryptoAddressKind kind;

  /// EVM lower-cased (its checksum casing carries no identity); Solana as
  /// written, because base58 is case-sensitive.
  final String address;

  const CryptoAddressInput(this.kind, this.address);
}

final RegExp _evm = RegExp(r'^0x[0-9a-fA-F]{40}$');
final RegExp _base58 = RegExp(r'^[1-9A-HJ-NP-Za-km-z]{32,44}$');
final RegExp _digit = RegExp(r'[0-9]');

/// The address in [raw] — bare, `$`-prefixed, or the last path segment of an
/// explorer or DEX link — or null when there is none.
CryptoAddressInput? detectCryptoAddress(String raw) {
  final text = raw.trim().replaceFirst(RegExp(r'^\$'), '');
  final uri = Uri.tryParse(text);
  final candidates = uri != null && uri.hasScheme ? uri.pathSegments.reversed : [text];
  return candidates.map(_addressIn).nonNulls.firstOrNull;
}

CryptoAddressInput? _addressIn(String text) {
  if (_evm.hasMatch(text)) {
    return CryptoAddressInput(CryptoAddressKind.evm, text.toLowerCase());
  }
  // Real mints contain digits; a long run of letters is far more likely a
  // name being typed than an address.
  if (_base58.hasMatch(text) && _digit.hasMatch(text)) {
    return CryptoAddressInput(CryptoAddressKind.solana, text);
  }
  return null;
}

/// True when two addresses name the same contract: EVM case-insensitively,
/// everything else exactly.
bool sameCryptoAddress(String a, String b) =>
    _evm.hasMatch(a) && _evm.hasMatch(b) ? a.toLowerCase() == b.toLowerCase() : a == b;

/// `0x6982…1933`, `EKpQ…zcjm` — enough to recognise an address at a glance.
String shortCryptoAddress(String address) {
  final evm = _evm.hasMatch(address);
  final head = evm ? 6 : 4;
  if (address.length <= head + 5) return address;
  return '${address.substring(0, head)}…${address.substring(address.length - 4)}';
}

/// Network names as their projects write them. Proper nouns, so they are not
/// translated; an unknown network id is shown as the price host spells it.
const Map<String, String> kCryptoChainNames = {
  'ethereum': 'Ethereum',
  'base': 'Base',
  'bsc': 'BNB Chain',
  'arbitrum': 'Arbitrum',
  'polygon': 'Polygon',
  'optimism': 'Optimism',
  'avalanche': 'Avalanche',
  'solana': 'Solana',
  'blast': 'Blast',
  'linea': 'Linea',
  'zksync': 'zkSync',
  'scroll': 'Scroll',
  'mantle': 'Mantle',
  'sonic': 'Sonic',
  'abstract': 'Abstract',
  'unichain': 'Unichain',
  'hyperevm': 'HyperEVM',
};

String cryptoChainName(String chain) => kCryptoChainNames[chain.toLowerCase()] ?? chain;
