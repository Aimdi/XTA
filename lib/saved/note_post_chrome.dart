import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/saved/local_post_logic.dart';

class NoteAvatar extends StatelessWidget {
  const NoteAvatar({super.key});

  @override
  Widget build(BuildContext context) => CircleAvatar(
    radius: 20,
    backgroundColor: Theme.of(context).colorScheme.primaryContainer,
    child: Icon(Icons.person_outline, size: 24, color: Theme.of(context).colorScheme.onPrimaryContainer),
  );
}

class NoteLocalIdentity extends StatelessWidget {
  const NoteLocalIdentity({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);
    return Wrap(
      spacing: 6,
      crossAxisAlignment: WrapCrossAlignment.center,
      children: [
        Text(l10n.local_note_author, style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700)),
        Text(
          '@${l10n.local_note_handle}',
          style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
        ),
      ],
    );
  }
}

/// Focus after the route settles so the keyboard doesn't resize a moving page.
class NoteComposeField extends StatefulWidget {
  final TextEditingController controller;
  final bool readOnly;
  final ValueChanged<String> onChanged;
  final String hint;

  const NoteComposeField({
    super.key,
    required this.controller,
    required this.readOnly,
    required this.onChanged,
    required this.hint,
  });

  @override
  State<NoteComposeField> createState() => _NoteComposeFieldState();
}

class _NoteComposeFieldState extends State<NoteComposeField> {
  final _focus = FocusNode();
  Animation<double>? _routeAnimation;
  bool _requestedFocus = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _routeAnimation?.removeStatusListener(_onStatus);
    _routeAnimation = ModalRoute.of(context)?.animation;
    _routeAnimation?.addStatusListener(_onStatus);
    if (_routeAnimation == null || _routeAnimation!.isCompleted) {
      _onStatus(AnimationStatus.completed);
    }
  }

  void _onStatus(AnimationStatus status) {
    if (status != AnimationStatus.completed || _requestedFocus) return;
    _requestedFocus = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _focus.requestFocus();
    });
  }

  @override
  void dispose() {
    _routeAnimation?.removeStatusListener(_onStatus);
    _focus.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => TextField(
    controller: widget.controller,
    focusNode: _focus,
    readOnly: widget.readOnly,
    minLines: 5,
    maxLines: null,
    maxLength: localPostMaxLength,
    textCapitalization: TextCapitalization.sentences,
    style: Theme.of(context).textTheme.bodyLarge?.copyWith(fontSize: 20, height: 1.4),
    onChanged: widget.onChanged,
    decoration: InputDecoration(
      hintText: widget.hint,
      filled: false,
      border: InputBorder.none,
      enabledBorder: InputBorder.none,
      focusedBorder: InputBorder.none,
      contentPadding: EdgeInsets.zero,
      counterText: '',
    ),
  );
}
