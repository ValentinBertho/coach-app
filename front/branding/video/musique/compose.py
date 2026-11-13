"""
Compose la musique de la vidéo de présentation : une boucle électro douce, 110 BPM, en la mineur
(la – fa – do – sol). Tout est synthétisé ici, sans échantillon ni boucle d'un tiers : la piste
appartient au projet et peut être utilisée partout sans droits à payer.

    python3 compose.py [sortie.wav] [durée-en-secondes]

Seule dépendance : numpy. ffmpeg ajoute ensuite l'écho et règle le volume (voir README).
"""
import sys
import wave

import numpy as np

SR = 44100
BPM = 110
BEAT = 60 / BPM
BAR = 4 * BEAT
out = sys.argv[1] if len(sys.argv) > 1 else 'darilab-theme.wav'
duration = float(sys.argv[2]) if len(sys.argv) > 2 else 100.0
bars = int(np.ceil(duration / BAR)) + 1
n = int(bars * BAR * SR)
rng = np.random.default_rng(7)


def hz(midi):
    return 440.0 * 2 ** ((midi - 69) / 12)


# la mineur, fa majeur, do majeur, sol majeur (notes MIDI)
CHORDS = [
    [57, 60, 64],  # la do mi
    [53, 57, 60],  # fa la do
    [48, 52, 55],  # do mi sol
    [55, 59, 62],  # sol si ré
]
ROOTS = [45, 41, 48, 43]


def env(length, attack, release):
    e = np.ones(length)
    a = min(int(attack * SR), length)
    r = min(int(release * SR), length - a)
    if a:
        e[:a] = np.linspace(0, 1, a)
    if r:
        e[length - r:] = np.linspace(1, 0, r)
    return e


def lowpass(x, cutoff):
    """Filtre passe-bas du premier ordre, appliqué deux fois (pente plus douce à l'oreille)."""
    a = np.exp(-2 * np.pi * cutoff / SR)
    for _ in range(2):
        y = np.empty_like(x)
        acc = 0.0
        # boucle explicite : le filtre est récursif
        for i in range(len(x)):
            acc = (1 - a) * x[i] + a * acc
            y[i] = acc
        x = y
    return x


def add(track, start, sig):
    i = int(start * SR)
    j = min(i + len(sig), len(track))
    if i < len(track):
        track[i:j] += sig[: j - i]


def pad_voice(freq, length):
    t = np.arange(length) / SR
    s = np.zeros(length)
    for detune in (-0.12, 0.0, 0.12):
        f = freq * 2 ** (detune / 12)
        for h, amp in ((1, 1.0), (2, 0.35), (3, 0.15), (4, 0.06)):
            s += amp * np.sin(2 * np.pi * f * h * t + rng.uniform(0, 2 * np.pi))
    return s / 6


def pluck(freq, length):
    """Corde pincée (Karplus-Strong)."""
    period = int(SR / freq)
    buf = rng.uniform(-1, 1, period)
    outv = np.empty(length)
    for i in range(length):
        outv[i] = buf[i % period]
        buf[i % period] = 0.497 * (buf[i % period] + buf[(i + 1) % period])
    return outv * env(length, 0.002, 0.05)


def kick():
    length = int(0.35 * SR)
    t = np.arange(length) / SR
    f = 45 + 75 * np.exp(-t * 28)
    phase = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(phase) * np.exp(-t * 9)


def hat():
    length = int(0.06 * SR)
    noise = rng.uniform(-1, 1, length)
    noise = noise - lowpass(noise, 6000)  # passe-haut
    return noise * np.exp(-np.arange(length) / SR * 70)


def clap():
    length = int(0.18 * SR)
    noise = rng.uniform(-1, 1, length)
    band = lowpass(noise, 3000) - lowpass(noise, 900)
    t = np.arange(length) / SR
    return band * np.exp(-t * 22) * 2.5


pads = np.zeros(n)
plucks = np.zeros(n)
bass = np.zeros(n)
drums = np.zeros(n)

K, H, C = kick(), hat(), clap()

for b in range(bars):
    chord = CHORDS[b % 4]
    start = b * BAR
    # Plan : 0-3 nappe seule · 4-7 + arpège · 8+ + basse et grosse caisse · 12+ + charleston et
    # claquement · 4 dernières mesures : on retire la rythmique.
    outro = b >= bars - 4
    length = int(BAR * SR) + int(0.4 * SR)
    voice = sum(pad_voice(hz(m), length) for m in chord)
    add(pads, start, voice * env(length, 0.6, 0.6))

    if b >= 4:
        pattern = [0, 1, 2, 1, 0, 2, 1, 2]
        for k, idx in enumerate(pattern):
            note = chord[idx] + 12
            add(plucks, start + k * BEAT / 2, pluck(hz(note), int(0.45 * SR)) * (0.9 if k % 2 == 0 else 0.6))

    if 8 <= b and not outro:
        root = hz(ROOTS[b % 4])
        for k in range(8):
            length_b = int(BEAT / 2 * SR * 0.9)
            t = np.arange(length_b) / SR
            tone = np.sin(2 * np.pi * root * t) + 0.25 * np.sin(2 * np.pi * root * 2 * t)
            tone = np.tanh(1.6 * tone) * env(length_b, 0.005, 0.04)
            add(bass, start + k * BEAT / 2, tone * (1.0 if k % 2 == 0 else 0.7))
        for k in range(4):
            add(drums, start + k * BEAT, K * 0.9)
    if 12 <= b and not outro:
        for k in range(8):
            if k % 2 == 1:
                add(drums, start + k * BEAT / 2, H * 0.25)
        for k in (1, 3):
            add(drums, start + k * BEAT, C * 0.35)

pads = lowpass(pads, 1800)
mix = 0.32 * pads + 0.22 * plucks + 0.30 * bass + 0.55 * drums
mix = mix[: int(duration * SR)]
fade = int(3.0 * SR)
mix[-fade:] *= np.linspace(1, 0, fade)
mix[: int(0.05 * SR)] *= np.linspace(0, 1, int(0.05 * SR))
mix /= np.max(np.abs(mix)) * 1.12

stereo = np.stack([mix, mix], axis=1)
# Léger élargissement : l'arpège un peu plus à gauche, décalé de quelques millisecondes à droite.
shift = int(0.012 * SR)
stereo[shift:, 1] += 0.08 * np.roll(plucks[: len(mix)], 0)[:-shift] / (np.max(np.abs(plucks)) + 1e-9)
stereo /= np.max(np.abs(stereo)) * 1.05

with wave.open(out, 'wb') as w:
    w.setnchannels(2)
    w.setsampwidth(2)
    w.setframerate(SR)
    w.writeframes((stereo * 32767).astype('<i2').tobytes())
print(f'{out} : {duration:.1f} s, {bars} mesures à {BPM} BPM')
