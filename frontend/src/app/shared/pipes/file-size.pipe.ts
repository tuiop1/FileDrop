import { Pipe, PipeTransform } from '@angular/core';

import { formatBytes } from '../utils/format.utils';

@Pipe({
  name: 'fileSize',
  standalone: true,
})
export class FileSizePipe implements PipeTransform {
  transform(bytes: number): string {
    return formatBytes(bytes);
  }
}
