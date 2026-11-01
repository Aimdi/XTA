import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/client/client.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/stocks/stocks_followed_posts.dart';
import 'package:xta/plugins/stocks/stocks_section_header.dart';

/// The two parts of a ticker's posts.
enum FollowedPart { followed, others }

/// What the top part of a ticker's posts holds, and where the rest starts.
@immutable
class FollowedSection {
  final List<TweetChain> top;
  final String? firstOther;

  const FollowedSection({this.top = const [], this.firstOther});

  /// The part whose label goes above [chainId], if one starts there.
  FollowedPart? partStartingAt(String chainId) => switch (chainId) {
    _ when top.isEmpty => null,
    _ when chainId == top.first.id => FollowedPart.followed,
    _ when chainId == firstOther => FollowedPart.others,
    _ => null,
  };
}

/// Kept with a ticker's feed: the first page sets it, later pages read it.
class FollowedSectionStore extends Store<FollowedSection> {
  FollowedSectionStore() : super(const FollowedSection());

  /// The first page: [top] above [page]'s other posts.
  List<TweetChain> first(List<TweetChain> top, List<TweetChain> page) {
    final others = withoutSection(page, top);
    update(FollowedSection(top: top, firstOther: top.isEmpty ? null : others.firstOrNull?.id));
    return [...top, ...others];
  }

  /// A later page without the top part's posts. When the first page had no
  /// other post, this page's first starts the second part.
  List<TweetChain> next(List<TweetChain> page) {
    final others = withoutSection(page, state.top);
    if (state.top.isNotEmpty && state.firstOther == null && others.isNotEmpty) {
      update(FollowedSection(top: state.top, firstOther: others.first.id));
    }
    return others;
  }
}

/// [post] under the label of the part it starts, if it starts one. Rows are
/// built once per post, so the label follows [section] on its own.
Widget followedSectionChain(FollowedSectionStore section, TweetChain chain, Widget post) =>
    ScopedBuilder<FollowedSectionStore, FollowedSection>(
      store: section,
      distinct: (state) => state.partStartingAt(chain.id),
      onState: (context, state) {
        final part = state.partStartingAt(chain.id);
        return Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (part != null) FollowedPartLabel(part: part),
            post,
          ],
        );
      },
    );

/// "Accounts you follow" over the top part, "Everyone else" over the rest.
class FollowedPartLabel extends StatelessWidget {
  final FollowedPart part;

  const FollowedPartLabel({super.key, required this.part});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    // Its own node: a heading, not the first line of the post under it.
    return Semantics(
      container: true,
      child: StocksSectionHeader(
        key: ValueKey('followed-part-${part.name}'),
        title: switch (part) {
          FollowedPart.followed => l10n.plugin_stocks_posts_followed,
          FollowedPart.others => l10n.plugin_stocks_posts_others,
        },
      ),
    );
  }
}
