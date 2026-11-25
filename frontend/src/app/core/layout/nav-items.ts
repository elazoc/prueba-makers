import { Role } from '../api/dto';
import { IconName } from '../../shared/ui/icons';

export interface NavItem {
  label: string;
  icon: IconName;
  link: string;
}

export const NAV_ITEMS: Record<Role, NavItem[]> = {
  USER: [{ label: 'Mis préstamos', icon: 'wallet', link: '/prestamos' }],
  ADMIN: [
    { label: 'Solicitudes', icon: 'inbox', link: '/admin/solicitudes' },
    { label: 'Usuarios', icon: 'users', link: '/admin/usuarios' },
  ],
};
