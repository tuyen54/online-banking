import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'maskAccount',
  standalone: true
})
export class MaskAccountPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    if (!value) return '******';
    if (value.startsWith('******')) return value;
    if (value.length <= 4) return '******' + value;
    return '******' + value.substring(value.length - 4);
  }
}

