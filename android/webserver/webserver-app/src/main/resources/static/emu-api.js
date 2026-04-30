class EmuApi {
  constructor() {
    this.baseUrl = location.protocol+'//'+location.host+'/emu'
    this.baseNano = 0n
    this.baseDate = new Date()
  }

  async noteOn(note, velocity, timestamp) {
    const time = shiftDateByMs(this.baseDate, timestamp)
    const ts = BigInt(this.baseNano) + BigInt(timestamp) * 1000000n
    const replacer = (key, value) => {
      // Если значение - BigInt, преобразуем его в строку
      if (typeof value === 'bigint') {
        return value.toString(); // Преобразуем BigInt в строку
      }
      // В противном случае, возвращаем значение как есть
      return value;
    };
    const json = JSON.stringify({
      type:      "noteOn", 
      note:      note, 
      channel:   1, 
      velocity:  velocity, 
      timestamp: ts, 
      time:      getDateTimeString(time)
    }, replacer)
    const url = this.baseUrl + '/event'
    fetch(url, {
      method: 'POST',
      body: json
    })
  }

  async noteOff(note, velocity, timestamp) {
    const time = shiftDateByMs(this.baseDate, timestamp)
    const ts = BigInt(this.baseNano) + BigInt(timestamp) * 1000000n
    const replacer = (key, value) => {
      // Если значение - BigInt, преобразуем его в строку
      if (typeof value === 'bigint') {
        return value.toString(); // Преобразуем BigInt в строку
      }
      // В противном случае, возвращаем значение как есть
      return value;
    };
    const json = JSON.stringify({
      type:      "noteOff", 
      note:      note, 
      channel:   1, 
      velocity:  velocity, 
      timestamp: ts, 
      time:      getDateTimeString(time)
    }, replacer)
    const url = this.baseUrl + '/event'
    fetch(url, {
      method: 'POST',
      body: json
    })
  }
}

function shiftDateByMs(originalDate, millisecondsToAdd) {
  // Получаем временную метку текущей даты в миллисекундах
  const originalTime = originalDate.getTime();

  // Вычисляем новую временную метку
  const newTime = originalTime + millisecondsToAdd;

  // Создаём и возвращаем новый объект Date с новой меткой
  return new Date(newTime);
}

function getDateTimeString(date) {
  //const now = new Date();

  const year = String(date.getUTCFullYear()).padStart(4, '0');
  const month = String(date.getUTCMonth() + 1).padStart(2, '0'); // getUTCMonth() возвращает 0-11
  const day = String(date.getUTCDate()).padStart(2, '0');

  const hours = String(date.getUTCHours()).padStart(2, '0');
  const minutes = String(date.getUTCMinutes()).padStart(2, '0');
  const seconds = String(date.getUTCSeconds()).padStart(2, '0');

  // Получаем миллисекунды (3 цифры) и оставшиеся наносекунды (до 9 цифр в сумме)
  const milliseconds = date.getUTCMilliseconds();
  const nanoseconds = (date.getMicroseconds && date.getMicroseconds() * 1000) || 0; // Microseconds не поддерживаются везде
  const totalNanoseconds = milliseconds * 1000000 + nanoseconds;

  const nanosStr = String(totalNanoseconds).padStart(9, '0').substring(0, 9); // Обрезаем до 9 знаков

  return `${year}-${month}-${day}T${hours}:${minutes}:${seconds}.${nanosStr}Z`;
}

function getDurationInMilliseconds(dateString1, dateString2) {
  // Преобразуем строки в объекты Date
  // Формат YYYY-MM-DDThh:mm:ss.nnnnnnnnnZ совместим с ISO 8601, который понимает Date.parse()
  const date1 = new Date(dateString1);
  const date2 = new Date(dateString2);

  // Проверим, валидны ли даты
  if (isNaN(date1.getTime()) || isNaN(date2.getTime())) {
    throw new Error("Одна или обе строки дат недействительны.");
  }

  // Вычисляем разницу в миллисекундах
  const durationMs = date2.getTime() - date1.getTime();

  return durationMs;
}

