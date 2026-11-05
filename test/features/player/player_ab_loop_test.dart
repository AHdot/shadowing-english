import 'package:flutter_test/flutter_test.dart';
import 'package:common_learn_english/features/player/presentation/player_mock_state.dart';

void main() {
  group('A-B playback loop', () {
    test('requires B point to be later than A point', () {
      final PlayerMockState state = PlayerMockState();
      expect(state.setAbLoopStart(12000), isTrue);
      expect(state.setAbLoopEnd(12000), isFalse);
      expect(state.setAbLoopEnd(11000), isFalse);
      expect(state.hasAbLoop, isFalse);
    });

    test('restarts at A when playback reaches B', () {
      final PlayerMockState state = PlayerMockState();
      state.setAbLoopStart(12000);
      expect(state.setAbLoopEnd(18500), isTrue);
      expect(state.hasAbLoop, isTrue);
      expect(state.restartLoopAtTimestamp(18499), isFalse);
      expect(state.restartLoopAtTimestamp(18500), isTrue);
      expect(state.positionMs, 12000);
    });

    test('changing A clears the previous B point', () {
      final PlayerMockState state = PlayerMockState();
      state.setAbLoopStart(1000);
      state.setAbLoopEnd(5000);
      state.setAbLoopStart(3000);
      expect(state.abLoopStartMs, 3000);
      expect(state.abLoopEndMs, isNull);
      expect(state.hasAbLoop, isFalse);
    });

    test('clearing A-B does not change single sentence loop state', () {
      final PlayerMockState state = PlayerMockState();
      state.setAbLoopStart(1000);
      state.setAbLoopEnd(5000);
      state.clearAbLoop();
      expect(state.hasAbLoop, isFalse);
      expect(state.isLooping, isFalse);
    });
  });
}
